package com.growthpush;

import android.content.Context;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.growthbeat.Growthbeat;
import com.growthbeat.GrowthbeatThreadExecutor;
import com.growthbeat.Logger;
import com.growthbeat.Preference;
import com.growthbeat.http.GrowthbeatHttpClient;
import com.growthbeat.message.GrowthMessage;
import com.growthbeat.message.handler.ShowMessageHandler;
import com.growthbeat.utils.AppUtils;
import com.growthbeat.utils.DeviceUtils;
import com.growthpush.handler.DefaultReceiveHandler;
import com.growthpush.handler.ReceiveHandler;
import com.growthpush.model.Client;
import com.growthpush.model.ClientV4;
import com.growthpush.model.Environment;
import com.growthpush.model.Event;
import com.growthpush.model.Tag;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class GrowthPush {

    private static final GrowthPush instance = new GrowthPush();
    private final Logger logger = new Logger(GrowthPushConstants.LOGGER_DEFAULT_TAG);
    private final GrowthbeatHttpClient httpClient = new GrowthbeatHttpClient(GrowthPushConstants.HTTP_CLIENT_DEFAULT_BASE_URL,
        GrowthPushConstants.HTTP_CLIENT_DEFAULT_CONNECT_TIMEOUT, GrowthPushConstants.HTTP_CLIENT_DEFAULT_READ_TIMEOUT);
    private final Preference preference = new Preference(GrowthPushConstants.PREFERENCE_DEFAULT_FILE_NAME);
    private final GrowthbeatThreadExecutor pushExecutor = new GrowthbeatThreadExecutor();
    private final GrowthbeatThreadExecutor analyticsExecutor = new GrowthbeatThreadExecutor();

    private ClientV4 client = null;
    private Semaphore semaphore = new Semaphore(1);
    private CountDownLatch latch = new CountDownLatch(1);
    private ReceiveHandler receiveHandler = new DefaultReceiveHandler();

    private String applicationId;
    private String credentialId;
    private String senderId;
    private Environment environment = null;

    private boolean initialized = false;

    private GrowthPush() {
        super();
    }

    public static GrowthPush getInstance() {
        return instance;
    }

    public void initialize(final Context context, final String applicationId, final String credentialId, final Environment environment) {
        this.initialize(context, applicationId, credentialId, environment, true);
    }

    public void initialize(final Context context, final String applicationId, final String credentialId, final Environment environment,
                           final boolean adInfoEnabled) {

        if (initialized)
            return;

        initialized = true;

        if (context == null) {
            logger.warning("The context parameter cannot be null.");
            return;
        }

        this.applicationId = applicationId;
        this.credentialId = credentialId;
        this.environment = environment;

        Growthbeat.getInstance().initialize(context, applicationId, credentialId);
        GrowthMessage.getInstance().initialize(context, applicationId, credentialId);

        this.preference.setContext(Growthbeat.getInstance().getContext());

        pushExecutor.execute(new Runnable() {
            @Override
            public void run() {

                com.growthbeat.model.Client growthbeatClient = Growthbeat.getInstance().waitClient();
                Client oldClient = Client.load();

                if (oldClient != null) {
                    if (oldClient.getGrowthbeatClientId() != null &&
                        oldClient.getGrowthbeatClientId().equals(growthbeatClient.getId())) {

                        logger.info(String.format("Client found. To Convert the Client to ClientV4. (id:%s)", growthbeatClient.getId()));
                        createClient(growthbeatClient.getId(), oldClient.getToken());
                    } else {
                        logger.info(String.format("Disabled Client found. Create a new ClientV4. (id:%s)", growthbeatClient.getId()));
                        createClient(growthbeatClient.getId(), null);
                    }
                    Client.clear();
                } else {
                    ClientV4 clientV4 = ClientV4.load();

                    if (clientV4 == null) {
                        logger.info(String.format("Create a new ClientV4. (id:%s)", growthbeatClient.getId()));
                        createClient(growthbeatClient.getId(), null);
                    } else if (!clientV4.getId().equals(growthbeatClient.getId())) {
                        logger.info(String.format("Disabled ClientV4 found. Create a new ClientV4. (id:%s)", growthbeatClient.getId()));
                        clearClient();
                        createClient(growthbeatClient.getId(), null);
                    } else if (environment != clientV4.getEnvironment()) {
                        logger.info(String.format("ClientV4 found. Update environment. (environment:%s)", environment.toString()));
                        updateClient(growthbeatClient.getId(), clientV4.getToken());
                    } else {
                        logger.info(String.format("ClientV4 found. (id:%s)", clientV4.getId()));
                        client = clientV4;
                    }
                }

                if (adInfoEnabled) {
                    setAdvertisingId();
                    setTrackingEnabled();
                }
                setDeviceTags();

            }
        });

    }

    public void requestRegistrationId(final String senderId) {

        if (!initialized) {
            logger.warning("Growth Push must be initialize.");
            return;
        }

        this.senderId = senderId;

        pushExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String token = registerGCM(Growthbeat.getInstance().getContext());
                if (token != null) {
                    logger.info("GCM registration token: " + token);
                    registerClient(token);
                }
            }
        });

    }

    public String registerGCM(final Context context) {
        if (this.senderId == null)
            return null;

        try {
            InstanceID instanceID = InstanceID.getInstance(context);
            return instanceID.getToken(this.senderId, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
        } catch (Exception e) {
            return null;
        }
    }

    public void registerClient(final String registrationId) {
        pushExecutor.execute(new Runnable() {

            @Override
            public void run() {
                if (registrationId == null)
                    return;

                waitClientRegistration();

                if (client.getToken() == null ||
                    (client.getToken() != null && !registrationId.equals(client.getToken()))) {

                    updateClient(client.getId(), registrationId);
                }
            }
        });
    }

    private void createClient(final String growthbeatClientId, final String registrationId) {

        try {

            semaphore.acquire();

            ClientV4 loadClient = ClientV4.load();
            if (loadClient != null) {
                this.client = loadClient;
                logger.info(String.format("ClientV4 already Created... (growthbeatClientId: %s, token: %s, environment: %s)",
                    growthbeatClientId, loadClient.getToken(), environment));
                return;
            }

            logger.info(String.format("Create client... (growthbeatClientId: %s, token: %s, environment: %s)", growthbeatClientId,
                registrationId, environment));
            client = ClientV4.attach(growthbeatClientId, applicationId, credentialId, registrationId, environment);
            logger.info(String.format("Create client success (clientId: %s)", client.getId()));
            ClientV4.save(client);

        } catch (InterruptedException e) {
        } catch (GrowthPushException e) {
            logger.error(String.format("Create client fail. %s", e.getMessage()));
        } finally {
            semaphore.release();
            latch.countDown();
        }

    }

    private void updateClient(final String growthbeatClientId, final String registrationId) {

        try {

            logger.info(String.format("Updating client... (growthbeatClientId: %s, token: %s, environment: %s)", growthbeatClientId,
                registrationId, environment));
            ClientV4 updatedClient = ClientV4.attach(growthbeatClientId, applicationId, credentialId, registrationId, environment);
            logger.info(String.format("Update client success (clientId: %s)", growthbeatClientId));

            ClientV4.save(updatedClient);
            this.client = updatedClient;

        } catch (GrowthPushException e) {
            logger.error(String.format("Update client fail. %s", e.getMessage()));
        } finally {
            latch.countDown();
        }

    }

    public void trackEvent(final String name) {
        trackEvent(name, null);
    }

    public void trackEvent(final String name, final String value) {
        trackEvent(name, value, null);
    }

    public void trackEvent(final String name, final String value, final ShowMessageHandler handler) {
        trackEvent(Event.EventType.custom, name, value, handler);
    }

    public void trackEvent(final Event.EventType type, final String name, final String value, final ShowMessageHandler handler) {

        if (!initialized) {
            logger.info("call after initialized.");
            return;
        }

        analyticsExecutor.execute(new Runnable() {

            @Override
            public void run() {

                if (name == null) {
                    logger.warning("Event name cannot be null.");
                    return;
                }

                waitClientRegistration();

                logger.info(String.format("Sending event ... (name: %s, value: %s)", name, value));
                try {
                    Event event = Event.create(GrowthPush.getInstance().client.getId(), applicationId,
                        GrowthPush.getInstance().credentialId, type, name, value);
                    logger.info(String.format("Sending event success. (name: %s, value: %s)", name, value));

                    if (type != Event.EventType.message)
                        GrowthMessage.getInstance().receiveMessage(event.getGoalId(), client.getId(), handler);

                } catch (GrowthPushException e) {
                    logger.error(String.format("Sending event fail. %s", e.getMessage()));
                }

            }

        });
    }

    public void setTag(final String name) {
        setTag(name, null);
    }

    public void setTag(final String name, final String value) {
        setTag(Tag.TagType.custom, name, value);
    }

    private void setTag(final Tag.TagType type, final String name, final String value) {

        if (!initialized) {
            logger.info("call after initialized.");
            return;
        }

        analyticsExecutor.execute(new Runnable() {

            @Override
            public void run() {

                if (name == null) {
                    logger.warning("Tag name cannot be null.");
                    return;
                }

                Tag tag = Tag.load(type, name);
                if (tag != null && (value == null || value.equalsIgnoreCase(tag.getValue()))) {
                    logger.info(String.format("Tag exists with the same value. (name: %s, value: %s)", name, value));
                    return;
                }

                waitClientRegistration();

                logger.info(String.format("Sending tag... (key: %s, value: %s)", name, value));
                try {
                    Tag createdTag = Tag.create(GrowthPush.getInstance().client.getId(), applicationId, credentialId, type, name, value);
                    logger.info(String.format("Sending tag success"));
                    Tag.save(createdTag, type, name);
                } catch (GrowthPushException e) {
                    logger.error(String.format("Sending tag fail. %s", e.getMessage()));
                }

            }

        });
    }

    public void setDeviceTags() {
        setTag("Device", DeviceUtils.getModel());
        setTag("OS", "Android " + DeviceUtils.getOsVersion());
        setTag("Language", DeviceUtils.getLanguage());
        setTag("Time Zone", DeviceUtils.getTimeZone());
        setTag("Version", AppUtils.getaAppVersion(Growthbeat.getInstance().getContext()));
        setTag("Build", AppUtils.getAppBuild(Growthbeat.getInstance().getContext()));
    }

    private void setAdvertisingId() {
        analyticsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String advertisingId = DeviceUtils.getAdvertisingId().get();
                    if (advertisingId != null)
                        setTag(Tag.TagType.custom, "AdvertisingID", advertisingId);
                } catch (Exception e) {
                    logger.warning("Failed to get advertisingId: " + e.getMessage());
                }
            }
        });
    }

    private void setTrackingEnabled() {
        analyticsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Boolean trackingEnabled = DeviceUtils.getTrackingEnabled().get();
                    if (trackingEnabled != null)
                        setTag(Tag.TagType.custom, "TrackingEnabled", String.valueOf(trackingEnabled));
                } catch (Exception e) {
                    logger.warning("Failed to get trackingEnabled: " + e.getMessage());
                }

            }
        });
    }

    private void waitClientRegistration() {
        if (client == null) {
            try {
                latch.await();
            } catch (InterruptedException e) {
            }
        }
    }

    public ReceiveHandler getReceiveHandler() {
        return receiveHandler;
    }

    public void setReceiveHandler(ReceiveHandler receiveHandler) {
        this.receiveHandler = receiveHandler;
    }

    public Logger getLogger() {
        return logger;
    }

    public GrowthbeatHttpClient getHttpClient() {
        return httpClient;
    }

    public Preference getPreference() {
        return preference;
    }

    private void clearClient() {

        this.client = null;
        ClientV4.clear();

    }
}

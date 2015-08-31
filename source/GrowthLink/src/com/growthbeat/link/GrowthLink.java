package com.growthbeat.link;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.growthbeat.CatchableThread;
import com.growthbeat.GrowthbeatCore;
import com.growthbeat.GrowthbeatException;
import com.growthbeat.Logger;
import com.growthbeat.Preference;
import com.growthbeat.analytics.GrowthAnalytics;
import com.growthbeat.http.GrowthbeatHttpClient;
import com.growthbeat.link.callback.DefaultSynchronizationCallback;
import com.growthbeat.link.callback.SynchronizationCallback;
import com.growthbeat.link.handler.DefaultInstallReferrerReceiveHandler;
import com.growthbeat.link.handler.InstallReferrerReceiveHandler;
import com.growthbeat.link.model.Click;
import com.growthbeat.link.model.Synchronization;
import com.growthbeat.utils.AppUtils;

public class GrowthLink {

	private static final String LOGGER_DEFAULT_TAG = "GrowthLink";
	private static final String HTTP_CLIENT_DEFAULT_BASE_URL = "https://api.link.growthbeat.com/";
	private static final String DEFAULT_SYNCRONIZATION_URL = "http://gbt.io/l/synchronize";
	private static final String DEFAULT_FINGERPRINT_URL = "http://gbt.io/l/fingerprint";
	private static final int HTTP_CLIENT_DEFAULT_CONNECTION_TIMEOUT = 60 * 1000;
	private static final int HTTP_CLIENT_DEFAULT_SOCKET_TIMEOUT = 60 * 1000;
	private static final String PREFERENCE_DEFAULT_FILE_NAME = "growthlink-preferences";

	private static final String INSTALL_REFERRER_KEY = "installReferrer";
	private static final long INSTALL_REFERRER_TIMEOUT = 10 * 1000;
	private static final long FINGERPRINT_TIMEOUT = 10 * 1000;

	private static final GrowthLink instance = new GrowthLink();
	private final Logger logger = new Logger(LOGGER_DEFAULT_TAG);
	private final GrowthbeatHttpClient httpClient = new GrowthbeatHttpClient(HTTP_CLIENT_DEFAULT_BASE_URL,
			HTTP_CLIENT_DEFAULT_CONNECTION_TIMEOUT, HTTP_CLIENT_DEFAULT_SOCKET_TIMEOUT);
	private final Preference preference = new Preference(PREFERENCE_DEFAULT_FILE_NAME);

	private Context context = null;
	private String applicationId = null;
	private String credentialId = null;
	private String fingerprintParameters = null;

	private String syncronizationUrl = DEFAULT_SYNCRONIZATION_URL;
	private String fingerprintUrl = DEFAULT_FINGERPRINT_URL;

	private boolean initialized = false;
	private boolean firstSession = false;
	private CountDownLatch installReferrerLatch = new CountDownLatch(1);
	private CountDownLatch fingerprintLatch = new CountDownLatch(1);

	private SynchronizationCallback synchronizationCallback = new DefaultSynchronizationCallback();
	private InstallReferrerReceiveHandler installReferrerReceiveHandler = new DefaultInstallReferrerReceiveHandler();

	private GrowthLink() {
		super();
	}

	public static GrowthLink getInstance() {
		return instance;
	}

	public void initialize(final Context context, final String applicationId, final String credentialId) {
		if (initialized)
			return;
		initialized = true;

		if (context == null) {
			logger.warning("The context parameter cannot be null.");
			return;
		}

		this.context = context.getApplicationContext();
		this.applicationId = applicationId;
		this.credentialId = credentialId;

		GrowthbeatCore.getInstance().initialize(context, applicationId, credentialId);
		this.preference.setContext(GrowthbeatCore.getInstance().getContext());
		if (GrowthbeatCore.getInstance().getClient() == null
				|| (GrowthbeatCore.getInstance().getClient().getApplication() != null && !GrowthbeatCore.getInstance().getClient()
						.getApplication().getId().equals(applicationId))) {
			preference.removeAll();
		}

		GrowthAnalytics.getInstance().initialize(context, applicationId, credentialId);
		getFingerprintParameters();
		synchronize();
	}
	
	@SuppressLint("SetJavaScriptEnabled")
	public void getFingerprintParameters(){
		
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_TOAST,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
         
        final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final WebView webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setVisibility(View.INVISIBLE);
        webView.setWebViewClient( new WebViewClient(){
        	 public boolean shouldOverrideUrlLoading( WebView argWebView, String argString ){
        	 
        	  String requestString = argString;
        	  if( requestString.startsWith( "native://js?fingerprintParameters=" ) ){
        		  Log.d("request_string", requestString);
        		  Map<String, String> param;
        		  try {
        			  param = splitQuery(new URI(requestString));
        			  fingerprintParameters = param.get("fingerprintParameters");
					} catch (URISyntaxException e) {
						e.printStackTrace();
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
        		  wm.removeView(webView);
        		  
        		  fingerprintLatch.countDown();
        	  }
        	  return( true );
        	 }
        	});
        webView.loadUrl(fingerprintUrl); 
        // Viewを画面上に重ね合わせする
        wm.addView(webView, params);   
	}

	public void handleOpenUrl(Uri uri) {

		if (uri == null)
			return;

		final String clickId = uri.getQueryParameter("clickId");
		if (clickId == null) {
			logger.info("Unabled to get clickId from url.");
			return;
		}

		final String uuid = uri.getQueryParameter("uuid");
		if (uuid != null) {
			GrowthAnalytics.getInstance().setUUID(uuid);
		}

		final Handler handler = new Handler();
		new Thread(new Runnable() {
			@Override
			public void run() {

				logger.info("Deeplinking...");

				try {

					final Click click = Click.deeplink(GrowthbeatCore.getInstance().waitClient().getId(), clickId, firstSession,
							credentialId);
					if (click == null || click.getPattern() == null || click.getPattern().getLink() == null) {
						logger.error("Failed to deeplink.");
						return;
					}

					logger.info(String.format("Deeplink success. (clickId: %s)", click.getId()));

					handler.post(new Runnable() {
						@Override
						public void run() {

							Map<String, String> properties = new HashMap<String, String>();
							properties.put("linkId", click.getPattern().getLink().getId());
							properties.put("patternId", click.getPattern().getId());
							if (click.getPattern().getIntent() != null)
								properties.put("intentId", click.getPattern().getIntent().getId());

							if (firstSession)
								GrowthAnalytics.getInstance().track("GrowthLink", "Install", properties, null);

							GrowthAnalytics.getInstance().track("GrowthLink", "Open", properties, null);

							firstSession = false;

							if (click.getPattern().getIntent() != null) {
								GrowthbeatCore.getInstance().handleIntent(click.getPattern().getIntent());
							}

						}
					});

				} catch (GrowthbeatException e) {
					logger.info(String.format("Deeplink is not found.", e.getMessage()));
				}

			}

		}).start();

	}

	private void synchronize() {

		logger.info("Check initialization...");
		if (Synchronization.load() != null) {
			logger.info("Already initialized.");
			return;
		}

		firstSession = true;

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					logger.info("Waiting for fingerprint");
					fingerprintLatch.await(FINGERPRINT_TIMEOUT, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					logger.warning(String.format("Failed to fetch fingerprint in %d ms", FINGERPRINT_TIMEOUT));
				}

				logger.info("Synchronizing...");

				try {

					String version = AppUtils.getaAppVersion(context);
					final Synchronization synchronization = Synchronization.synchronize(applicationId, version, credentialId , fingerprintParameters);
					if (synchronization == null) {
						logger.error("Failed to Synchronize.");
						return;
					}

					Synchronization.save(synchronization);
					logger.info(String.format("Synchronize success. (browser: %s)", synchronization.getBrowser()));

					if (synchronization.getInstallReferrer() && getInstallReferrer() == null) {
						try {
							installReferrerLatch.await(INSTALL_REFERRER_TIMEOUT, TimeUnit.MILLISECONDS);
						} catch (InterruptedException e) {
							logger.warning(String.format("Failed to fetch install referrer in %d ms", INSTALL_REFERRER_TIMEOUT));
						}
					}

					new Handler(Looper.getMainLooper()).post(new Runnable() {
						@Override
						public void run() {
							String newInstallReferrer = getInstallReferrer();
							if (newInstallReferrer != null && newInstallReferrer.length() != 0) {
								String uriString = "?"
										+ newInstallReferrer.replace("growthlink.clickId", "clickId").replace("growthbeat.uuid", "uuid");
								handleOpenUrl(Uri.parse(uriString));
							} else if (!synchronization.getBrowser() && synchronization.getClickId() != null ) {
								String uriString = "?clickId=" + synchronization.getClickId();
								handleOpenUrl(Uri.parse(uriString));
							}
							
							if (GrowthLink.this.synchronizationCallback != null) {
								GrowthLink.this.synchronizationCallback.onComplete(synchronization);
							}
							
						}
					});

				} catch (GrowthbeatException e) {
					logger.info(String.format("Synchronization is not found. %s", e.getMessage()));
				}

			}

		}).start();

	}

	public Context getContext() {
		return context;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public String getCredentialId() {
		return credentialId;
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

	public String getSyncronizationUrl() {
		return syncronizationUrl;
	}

	public void setSyncronizationUrl(String syncronizationUrl) {
		this.syncronizationUrl = syncronizationUrl;
	}
	
	public String getFingerprintUrl() {
		return fingerprintUrl;
	}

	public void setFingerprintUrl(String fingerprintUrl) {
		this.fingerprintUrl = fingerprintUrl;
	}
	
	public String getInstallReferrer() {
		return this.preference.getString(INSTALL_REFERRER_KEY);
	}

	public void setInstallReferrer(String installReferrer) {
		this.preference.save(INSTALL_REFERRER_KEY, installReferrer);
		this.installReferrerLatch.countDown();
	}

	public SynchronizationCallback getSynchronizationCallback() {
		return synchronizationCallback;
	}

	public void setSynchronizationCallback(SynchronizationCallback synchronizationCallback) {
		this.synchronizationCallback = synchronizationCallback;
	}

	public InstallReferrerReceiveHandler getInstallReferrerReceiveHandler() {
		return installReferrerReceiveHandler;
	}

	public void setInstallReferrerReceiveHandler(InstallReferrerReceiveHandler installReferrerReceiveHandler) {
		this.installReferrerReceiveHandler = installReferrerReceiveHandler;
	}

	private static class Thread extends CatchableThread {

		public Thread(Runnable runnable) {
			super(runnable);
		}

		@Override
		public void uncaughtException(java.lang.Thread thread, Throwable e) {
			String link = "Uncaught Exception: " + e.getClass().getName();
			if (e.getMessage() != null)
				link += "; " + e.getMessage();
			GrowthLink.getInstance().getLogger().warning(link);
			e.printStackTrace();
		}

	}
	
	private Map<String, String> splitQuery(URI uri) throws UnsupportedEncodingException {
	    Map<String, String> query_pairs = new LinkedHashMap<String, String>();
	    String query = uri.getQuery();
	    String[] pairs = query.split("&");
	    for (String pair : pairs) {
	        int idx = pair.indexOf("=");
	        query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
	    }
	    return query_pairs;
	}

}

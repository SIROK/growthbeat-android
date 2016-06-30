package com.growthbeat.message.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import com.growthbeat.message.GrowthMessage;
import com.growthbeat.message.handler.ShowMessageHandler;
import com.growthbeat.message.model.Button;
import com.growthbeat.message.model.CardMessage;
import com.growthbeat.message.model.CloseButton;
import com.growthbeat.message.model.ImageButton;
import com.growthbeat.message.model.ScreenButton;
import com.growthbeat.message.model.Task;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

public class CardMessageFragment extends BaseMessageFragment {

    private CardMessage cardMessage = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final Object message = getArguments().get("message");
        if (message == null || !(message instanceof CardMessage))
            return null;

        final String uuid = getArguments().getString("uuid");
        this.cardMessage = (CardMessage) message;
        this.baseLayout = generateBaseLayout(cardMessage.getBackground());

        layoutMessage(cardMessage, uuid, new ShowMessageHandler.MessageRenderHandler() {
            @Override
            public void render() {
                renderMessage();
            }
        });

        return baseLayout;

    }

    private void renderMessage() {

        FrameLayout cardLayout = null;

        List<Button> screenButtons = extractButtons(Button.ButtonType.screen);

        if (!screenButtons.isEmpty()) {
            cardLayout = createScreenButtonLayout((ScreenButton) screenButtons.get(0));
        } else {
            cardLayout = createCardLayout();
        }

        addCloseButton(cardLayout);

        FrameLayout buttonLayout = createButtonLayout();

        int messageWidth = (int) (Math.max(cardLayout.getLayoutParams().width, buttonLayout.getLayoutParams().width));
        int messageHeight = (int) (Math.max(cardLayout.getLayoutParams().height, buttonLayout.getLayoutParams().height));

        FrameLayout messageLayout = new FrameLayout(getActivity().getApplicationContext());
        FrameLayout.LayoutParams messageLayoutParams = new FrameLayout.LayoutParams(
            messageWidth, messageHeight);
        messageLayoutParams.gravity = Gravity.CENTER;
        messageLayout.setLayoutParams(messageLayoutParams);

        buttonLayout.setX((int) ((messageWidth - buttonLayout.getLayoutParams().width) * 0.5));
        buttonLayout.setY(messageHeight - buttonLayout.getLayoutParams().height);

        messageLayout.addView(cardLayout);
        messageLayout.addView(buttonLayout);

        baseLayout.addView(messageLayout);
    }

    private FrameLayout createCardLayout() {
        final int cardBaseWidth = (int) (cardMessage.getBaseWidth() * displayMetrics.density);
        final int cardBaseHeight = (int) (cardMessage.getBaseHeight() * displayMetrics.density);

        ImageView cardImageView = new ImageView(getActivity().getApplicationContext());
        cardImageView.setImageBitmap(getImageResource(cardMessage.getPicture().getUrl()));
        cardImageView.setScaleType(ScaleType.CENTER_INSIDE);
        cardImageView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View arg0, MotionEvent arg1) {
                return true;
            }

        });

        cardImageView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        float imageRatio = Math.min(1.0f, Math.min(
            (float) cardBaseWidth / cardImageView.getMeasuredWidth(), (float) cardBaseHeight / cardImageView.getMeasuredHeight()));

        FrameLayout cardLayout = new FrameLayout(getActivity().getApplicationContext());
        FrameLayout.LayoutParams cardLayoutParams = new FrameLayout.LayoutParams(
            (int) (cardImageView.getMeasuredWidth() * imageRatio),
            (int) (cardImageView.getMeasuredHeight() * imageRatio));
        cardLayoutParams.gravity = Gravity.CENTER;
        cardLayout.setLayoutParams(cardLayoutParams);

        cardLayout.addView(cardImageView);

        return cardLayout;
    }

    private FrameLayout createScreenButtonLayout(final ScreenButton screenButton) {

        final int cardBaseWidth = (int) (cardMessage.getBaseWidth() * displayMetrics.density);
        final int cardBaseHeight = (int) (cardMessage.getBaseHeight() * displayMetrics.density);

        TouchableImageView touchableImageView = new TouchableImageView(getActivity().getApplicationContext());
        touchableImageView.setScaleType(ScaleType.CENTER_INSIDE);
        touchableImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GrowthMessage.getInstance().selectButton(screenButton, cardMessage);
                finishActivity();
            }
        });
        touchableImageView.setImageBitmap(getImageResource(cardMessage.getPicture().getUrl()));

        touchableImageView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        float imageRatio = Math.min(1.0f, Math.min(
            (float) cardBaseWidth / touchableImageView.getMeasuredWidth(), (float) cardBaseHeight / touchableImageView.getMeasuredHeight()));

        FrameLayout cardLayout = new FrameLayout(getActivity().getApplicationContext());
        FrameLayout.LayoutParams cardLayoutParams = new FrameLayout.LayoutParams(
            (int) (touchableImageView.getMeasuredWidth() * imageRatio),
            (int) (touchableImageView.getMeasuredHeight() * imageRatio));
        cardLayoutParams.gravity = Gravity.CENTER;
        cardLayout.setLayoutParams(cardLayoutParams);

        cardLayout.addView(touchableImageView);

        return cardLayout;
    }

    private FrameLayout createButtonLayout() {

        List<Button> buttons = extractButtons(Button.ButtonType.image);
        Collections.reverse(buttons);

        FrameLayout buttonLayout = new FrameLayout(getActivity().getApplicationContext());

        if (buttons.size() < 1) {
            FrameLayout.LayoutParams buttonLayoutParams = new FrameLayout.LayoutParams(0, 0);
            buttonLayout.setLayoutParams(buttonLayoutParams);
            return buttonLayout;
        }

        final ImageButton imageButton = (ImageButton) buttons.get(0);

        int buttonBaseWidth = (int) (imageButton.getBaseWidth() * displayMetrics.density);
        int buttonBaseHeight = (int) (imageButton.getBaseHeight() * displayMetrics.density);

        TouchableImageView touchableImageView = new TouchableImageView(getActivity().getApplicationContext());
        touchableImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GrowthMessage.getInstance().selectButton(imageButton, cardMessage);
                finishActivity();
            }
        });
        touchableImageView.setImageBitmap(getImageResource(imageButton.getPicture().getUrl()));
        touchableImageView.setScaleType(ScaleType.CENTER_INSIDE);

        touchableImageView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        float imageRatio = Math.min(1.0f, Math.min(
            (float) buttonBaseWidth / touchableImageView.getMeasuredWidth(), (float) buttonBaseHeight / touchableImageView.getMeasuredHeight()));

        FrameLayout.LayoutParams buttonLayoutParams = new FrameLayout.LayoutParams(
            (int) (touchableImageView.getMeasuredWidth() * imageRatio),
            (int) (touchableImageView.getMeasuredHeight() * imageRatio));
        buttonLayout.setLayoutParams(buttonLayoutParams);

        buttonLayout.addView(touchableImageView);

        return buttonLayout;
    }

    private void addCloseButton(FrameLayout cardLayout) {

        List<Button> buttons = extractButtons(Button.ButtonType.close);

        if (buttons.size() < 1)
            return;

        final CloseButton closeButton = (CloseButton) buttons.get(0);

        int closeBaseWidth = (int) (closeButton.getBaseWidth() * displayMetrics.density);
        int closeBaseHeight = (int) (closeButton.getBaseHeight() * displayMetrics.density);
        int rightMargin = (int) (8 * displayMetrics.density);
        int topMargin = (int) (8 * displayMetrics.density);

        TouchableImageView touchableImageView = new TouchableImageView(getActivity().getApplicationContext());
        touchableImageView.setScaleType(ScaleType.CENTER_INSIDE);
        touchableImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GrowthMessage.getInstance().selectButton(closeButton, cardMessage);
                finishActivity();
            }
        });
        touchableImageView.setImageBitmap(getImageResource(closeButton.getPicture().getUrl()));

        touchableImageView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        float imageRatio = Math.min(1.0f, Math.min(
            (float) closeBaseWidth / touchableImageView.getMeasuredWidth(), (float) closeBaseHeight / touchableImageView.getMeasuredHeight()));

        FrameLayout closeLayout = new FrameLayout(getActivity().getApplicationContext());
        FrameLayout.LayoutParams closeLayoutParams = new FrameLayout.LayoutParams(
            (int) (touchableImageView.getMeasuredWidth() * imageRatio),
            (int) (touchableImageView.getMeasuredHeight() * imageRatio));
        closeLayoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
        closeLayoutParams.setMargins(0, topMargin, rightMargin, 0);
        closeLayout.setLayoutParams(closeLayoutParams);

        closeLayout.addView(touchableImageView);

        cardLayout.addView(closeLayout);
    }

    private List<Button> extractButtons(Button.ButtonType type) {

        List<Button> buttons = new ArrayList<Button>();

        for (Button button : cardMessage.getButtons()) {
            if (button.getType() == type) {
                buttons.add(button);
            }
        }

        return buttons;

    }

}

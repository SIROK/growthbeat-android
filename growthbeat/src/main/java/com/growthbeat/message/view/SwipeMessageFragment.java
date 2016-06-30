package com.growthbeat.message.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import com.growthbeat.message.GrowthMessage;
import com.growthbeat.message.handler.ShowMessageHandler;
import com.growthbeat.message.model.Button;
import com.growthbeat.message.model.CloseButton;
import com.growthbeat.message.model.ImageButton;
import com.growthbeat.message.model.Picture;
import com.growthbeat.message.model.SwipeMessage;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class SwipeMessageFragment extends BaseMessageFragment {

    private static final int INDICATOR_HEIGHT = 8;
    private static final int INDICATOR_TOP_MARGIN = 16;

    private SwipeMessage swipeMessage = null;
    private ViewPager viewPager = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Object message = getArguments().get("message");
        if (message == null || !(message instanceof SwipeMessage))
            return null;

        final String uuid = getArguments().getString("uuid");
        this.swipeMessage = (SwipeMessage) message;
        this.baseLayout = generateBaseLayout(swipeMessage.getBackground());

        layoutMessage(swipeMessage, uuid, new ShowMessageHandler.MessageRenderHandler() {
            @Override
            public void render() {
                renderMessage();
            }
        });

        return baseLayout;

    }

    private void renderMessage() {

        FrameLayout swipeLayout = createSwipeLayout();
        int swipeWidth = swipeLayout.getLayoutParams().width;
        int swipeHeight = swipeLayout.getLayoutParams().height;

        FrameLayout buttonLayout = createButtonLayout();
        int buttonHeight = buttonLayout.getLayoutParams().height;
        buttonLayout.setY(swipeHeight);

        FrameLayout indicatorLayout = createIndicatorLayout();
        FrameLayout.LayoutParams indicatorLayoutParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, (int) (INDICATOR_HEIGHT * displayMetrics.density));
        int indicatorTopMargin = (int) (INDICATOR_TOP_MARGIN * displayMetrics.density);
        indicatorLayoutParams.setMargins(0, swipeHeight + buttonHeight + indicatorTopMargin, 0, 0);
        indicatorLayout.setLayoutParams(indicatorLayoutParams);

        int messageWidth = swipeWidth;
        int messageHeight = swipeHeight + buttonHeight + indicatorTopMargin + indicatorLayout.getLayoutParams().height;

        FrameLayout messageLayout = new FrameLayout(getActivity().getApplicationContext());
        FrameLayout.LayoutParams messageLayoutParams = new FrameLayout.LayoutParams(
            messageWidth, messageHeight);
        messageLayoutParams.gravity = Gravity.CENTER;
        messageLayout.setLayoutParams(messageLayoutParams);

        messageLayout.addView(swipeLayout);
        messageLayout.addView(buttonLayout);
        messageLayout.addView(indicatorLayout);

        baseLayout.addView(messageLayout);
    }

    private FrameLayout createSwipeLayout() {
        final int swipeBaseWidth = (int) (swipeMessage.getBaseWidth() * displayMetrics.density);
        final int swipeBaseHeight = (int) (swipeMessage.getBaseHeight() * displayMetrics.density);

        FrameLayout swipeLayout = new FrameLayout(getActivity().getApplicationContext());
        FrameLayout.LayoutParams swipeLayoutParams = new FrameLayout.LayoutParams(
            swipeBaseWidth, swipeBaseHeight);
        swipeLayout.setLayoutParams(swipeLayoutParams);

        FrameLayout pagerLayout = new FrameLayout(getActivity().getApplicationContext());
        FrameLayout.LayoutParams pagerLayoutParams = new FrameLayout.LayoutParams(swipeBaseWidth, swipeBaseHeight);

        SwipePagerAdapter adapter = new SwipePagerAdapter();
        List<Picture> pictures = swipeMessage.getPictures();

        for (Picture picture : pictures) {
            ImageView imageView = new ImageView(getActivity().getApplicationContext());
            imageView.setImageBitmap(getImageResource(picture.getUrl()));
            imageView.setScaleType(ScaleType.CENTER_INSIDE);

            imageView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

            FrameLayout innerPictureLayout = new FrameLayout(getActivity().getApplicationContext());

            float imageRatio = Math.min(1.0f, Math.min(
                (float) swipeBaseWidth / imageView.getMeasuredWidth(), (float) swipeBaseHeight / imageView.getMeasuredHeight()));

            FrameLayout.LayoutParams innerPictureLayoutParams = new FrameLayout.LayoutParams(
                (int) (imageView.getMeasuredWidth() * imageRatio),
                (int) (imageView.getMeasuredHeight() * imageRatio));
            innerPictureLayoutParams.gravity = Gravity.CENTER;
            innerPictureLayout.setLayoutParams(innerPictureLayoutParams);

            innerPictureLayout.addView(imageView);

            FrameLayout pictureLayout = new FrameLayout(getActivity().getApplicationContext());
            FrameLayout.LayoutParams pictureLayoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
            pictureLayoutParams.gravity = Gravity.CENTER;
            pictureLayout.setLayoutParams(pictureLayoutParams);

            addCloseButton(innerPictureLayout);

            pictureLayout.addView(innerPictureLayout);

            adapter.add(pictureLayout);
        }

        viewPager = new ViewPager(getActivity());
        viewPager.setAdapter(adapter);
        pagerLayout.addView(viewPager, pagerLayoutParams);
        swipeLayout.addView(pagerLayout);

        return swipeLayout;
    }


    private FrameLayout createIndicatorLayout() {
        SwipePagerIndicator swipePagerIndicator = new SwipePagerIndicator();
        swipePagerIndicator.setViewPager(viewPager);
        FrameLayout layout = new FrameLayout(getActivity().getApplicationContext());
        layout.addView(swipePagerIndicator);
        return layout;
    }

    private void addCloseButton(FrameLayout swipeLayout) {

        List<Button> buttons = extractButtons(EnumSet.of(Button.ButtonType.close));

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
                GrowthMessage.getInstance().selectButton(closeButton, swipeMessage);
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

        swipeLayout.addView(closeLayout);
    }

    private FrameLayout createButtonLayout() {
        List<Button> buttons = extractButtons(EnumSet.of(Button.ButtonType.image));
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
                GrowthMessage.getInstance().selectButton(imageButton, swipeMessage);
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

        FrameLayout innerButtonLayout = new FrameLayout(getActivity().getApplicationContext());
        FrameLayout.LayoutParams innerButtonLayoutParams = new FrameLayout.LayoutParams(
            (int) (touchableImageView.getMeasuredWidth() * imageRatio),
            (int) (touchableImageView.getMeasuredHeight() * imageRatio));
        innerButtonLayoutParams.gravity = Gravity.CENTER;
        innerButtonLayout.setLayoutParams(innerButtonLayoutParams);

        innerButtonLayout.addView(touchableImageView);

        FrameLayout.LayoutParams buttonLayoutParams = new FrameLayout.LayoutParams(
            buttonBaseWidth,
            (int) (touchableImageView.getMeasuredHeight() * imageRatio));
        buttonLayout.setLayoutParams(buttonLayoutParams);

        buttonLayout.addView(innerButtonLayout);

        return buttonLayout;
    }

    private List<Button> extractButtons(EnumSet<Button.ButtonType> types) {

        List<Button> buttons = new ArrayList<Button>();

        for (Button button : swipeMessage.getButtons()) {
            if (types.contains(button.getType())) {
                buttons.add(button);
            }
        }

        return buttons;

    }

    private class SwipePagerAdapter extends PagerAdapter {
        private ArrayList<FrameLayout> itemList;

        public SwipePagerAdapter() {
            itemList = new ArrayList<FrameLayout>();
        }

        public void add(FrameLayout layout) {
            itemList.add(layout);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            FrameLayout layout = itemList.get(position);
            container.addView(layout);
            return layout;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getCount() {
            return itemList.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == (FrameLayout) object;
        }
    }

    private class SwipePagerIndicator extends View {
        private static final float DISTANCE = 24.0f;
        private static final float RADIUS = 4.0f;

        private ViewPager viewPager;
        private int position;
        private Paint paint;

        public SwipePagerIndicator() {
            super(getActivity());

            paint = new Paint();
            paint.setStrokeWidth(1);
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setAntiAlias(true);
        }

        public void setViewPager(ViewPager viewPager) {
            this.viewPager = viewPager;

            this.viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    setPosition(position);
                    invalidate();
                }

                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                }

                @Override
                public void onPageScrollStateChanged(int state) {
                }
            });
        }

        private void setPosition(int position) {
            this.position = position;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (viewPager == null) {
                return;
            }

            final int count = viewPager.getAdapter().getCount();
            final float longOffset = (getWidth() * 0.5f) + (DISTANCE * displayMetrics.density * 0.5f)
                - (count * DISTANCE * displayMetrics.density * 0.5f);
            final float shortOffset = getHeight() * 0.5f;

            for (int i = 0; i < count; i++) {
                if (position == i) {
                    paint.setColor(Color.WHITE);
                } else {
                    paint.setARGB(255, 30, 30, 30);
                }
                float cx = longOffset + (i * DISTANCE * displayMetrics.density);
                float cy = shortOffset;
                canvas.drawCircle(cx, cy, RADIUS * displayMetrics.density, paint);
            }

        }
    }
}

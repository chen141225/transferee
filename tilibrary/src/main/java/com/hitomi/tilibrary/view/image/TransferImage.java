package com.hitomi.tilibrary.view.image;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

/**
 * 平滑变化显示图片的 ImageView
 * 仅限于用于:从一个ScaleType==CENTER_CROP的ImageView，切换到另一个ScaleType=
 * FIT_CENTER的ImageView，或者反之 (使用同样的图片最好)
 */
public class TransferImage extends ImageView {

    public static final int STATE_TRANS_NORMAL = 0;
    public static final int STATE_TRANS_IN = 1; // 从缩略图到大图状态
    public static final int STATE_TRANS_OUT = 2; // 从大图到缩略图状态

    public static final int CATE_ANIMA_TOGETHER = 100; // 动画类型：位移和缩放同时进行
    public static final int CATE_ANIMA_APART = 200; // 动画类型：位移和缩放分开进行

    public static final int STAGE_IN_TRANSLATE = 201; // 平移
    public static final int STAGE_IN_SCALE = 202; // 缩放

    private final int backgroundColor = 0xFF000000;

    private int state = STATE_TRANS_NORMAL; // 当前动画状态
    private int cate = CATE_ANIMA_TOGETHER; // 当前动画类型
    private int stage = STAGE_IN_TRANSLATE; // 针对 CATE_ANIMA_APART 类型对话而言：当前动画的阶段

    private int originalWidth;
    private int originalHeight;
    private int originalLocationX;
    private int originalLocationY;
    private int backgroundAlpha = 0;
    private long duration = 300;
    private boolean transformStart = false;

    private Paint paint;
    private Matrix transMatrix;
    private Drawable transDrawable;

    private Transfrom transfrom;
    private OnTransferListener transformListener;

    public TransferImage(Context context) {
        this(context, null);
    }

    public TransferImage(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TransferImage(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        transMatrix = new Matrix();
        paint = new Paint();
        paint.setColor(backgroundColor);
        paint.setStyle(Style.FILL);
    }

    public void setOriginalInfo(int width, int height, int locationX, int locationY) {
        originalWidth = width;
        originalHeight = height;
        originalLocationX = locationX;
        originalLocationY = locationY;
        // 因为是屏幕坐标，所以要转换为该视图内的坐标，因为我所用的该视图是MATCH_PARENT，所以不用定位该视图的位置,如果不是的话，还需要定位视图的位置，然后计算mOriginalLocationX和mOriginalLocationY
//		originalLocationY = mOriginalLocationY - getStatusBarHeight(getContext());
    }

    /**
     * 用于开始进入的方法。 调用此方前，需已经调用过setOriginalInfo
     */
    public void transformIn() {
        cate = CATE_ANIMA_TOGETHER;
        state = STATE_TRANS_IN;
        transformStart = true;
        invalidate();
    }

    /**
     * 用于开始进入的方法(平移和放大动画分离)。 调用此方前，需已经调用过setOriginalInfo
     *
     * @param animaStage 动画阶段 :{@link #STAGE_IN_TRANSLATE} 平移，{@link #STAGE_IN_SCALE}
     */
    public void transformIn(int animaStage) {
        cate = CATE_ANIMA_APART;
        state = STATE_TRANS_IN;
        stage = animaStage;
        transformStart = true;
        invalidate();
    }

    /**
     * 用于开始退出的方法。 调用此方前，需已经调用过setOriginalInfo
     */
    public void transformOut() {
        cate = CATE_ANIMA_TOGETHER;
        state = STATE_TRANS_OUT;
        transformStart = true;
        invalidate();
    }

    /**
     * 获取伸缩动画执行的时间
     *
     * @return unit ：毫秒
     */
    public long getDuration() {
        return duration;
    }

    /**
     * 设置伸缩动画执行的时间
     *
     * @param duration unit ：毫秒
     */
    public void setDuration(long duration) {
        this.duration = duration;
    }

    /**
     * 获取当前的状态
     *
     * @return {@link #STATE_TRANS_NORMAL}, {@link #STATE_TRANS_IN}, {@link #STATE_TRANS_OUT}
     */
    public int getState() {
        return state;
    }

    /**
     * 初始化进入的变量信息
     */
    private void initTransform() {
        if (getDrawable() == null) return;
        if (getWidth() == 0 || getHeight() == 0) return;

        if (transDrawable == null) transDrawable = getDrawable();
        transfrom = new Transfrom();

        /** 下面为缩放的计算 */
        /* 计算初始的缩放值，初始值因为是CENTR_CROP效果，所以要保证图片的宽和高至少1个能匹配原始的宽和高，另1个大于 */
        float xSScale = originalWidth / ((float) transDrawable.getIntrinsicWidth());
        float ySScale = originalHeight / ((float) transDrawable.getIntrinsicHeight());
        float startScale = xSScale > ySScale ? xSScale : ySScale;
        transfrom.startScale = startScale;
        /* 计算结束时候的缩放值，结束值因为要达到FIT_CENTER效果，所以要保证图片的宽和高至少1个能匹配原始的宽和高，另1个小于 */
        float xEScale = getWidth() / ((float) transDrawable.getIntrinsicWidth());
        float yEScale = getHeight() / ((float) transDrawable.getIntrinsicHeight());
        float endScale = xEScale < yEScale ? xEScale : yEScale;
        if (cate == CATE_ANIMA_APART && stage == STAGE_IN_TRANSLATE) { // 平移阶段的动画，不缩放
            transfrom.endScale = startScale;
        } else {
            transfrom.endScale = endScale;
        }

        /**
         * 下面计算Canvas Clip的范围，也就是图片的显示的范围，因为图片是慢慢变大，并且是等比例的，所以这个效果还需要裁减图片显示的区域
         * ，而显示区域的变化范围是在原始CENTER_CROP效果的范围区域
         * ，到最终的FIT_CENTER的范围之间的，区域我用LocationSizeF更好计算
         * ，他就包括左上顶点坐标，和宽高，最后转为Canvas裁减的Rect.
         */
        /* 开始区域 */
        transfrom.startRect = new LocationSizeF();
        transfrom.startRect.left = originalLocationX;
        transfrom.startRect.top = originalLocationY;
        transfrom.startRect.width = originalWidth;
        transfrom.startRect.height = originalHeight;
        /* 结束区域 */
        transfrom.endRect = new LocationSizeF();
        float bitmapEndWidth = transDrawable.getIntrinsicWidth() * transfrom.endScale;// 图片最终的宽度
        float bitmapEndHeight = transDrawable.getIntrinsicHeight() * transfrom.endScale;// 图片最终的高度
        transfrom.endRect.left = (getWidth() - bitmapEndWidth) / 2;
        transfrom.endRect.top = (getHeight() - bitmapEndHeight) / 2;
        transfrom.endRect.width = bitmapEndWidth;
        transfrom.endRect.height = bitmapEndHeight;

        transfrom.rect = new LocationSizeF();
    }

    private void calcBmpMatrix() {
        if (getDrawable() == null || transfrom == null) return;
        if (transDrawable == null) {
            transDrawable = getDrawable();
        }

		/* 下面实现了CENTER_CROP的功能 */
        transMatrix.setScale(transfrom.scale, transfrom.scale);
        transMatrix.postTranslate(-(transfrom.scale * transDrawable.getIntrinsicWidth() / 2 - transfrom.rect.width / 2),
                -(transfrom.scale * transDrawable.getIntrinsicHeight() / 2 - transfrom.rect.height / 2));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getDrawable() == null) return;

        if (state == STATE_TRANS_IN || state == STATE_TRANS_OUT) {
            if (transformStart) {
                initTransform();
            }
            if (transfrom == null) {
                super.onDraw(canvas);
                return;
            }

            if (transformStart) {
                if (state == STATE_TRANS_IN) {
                    transfrom.initStartIn();
                } else {
                    transfrom.initStartOut();
                }
            }

            paint.setAlpha(backgroundAlpha);
            canvas.drawPaint(paint);

            int saveCount = canvas.getSaveCount();
            canvas.save();
            // 先得到图片在此刻的图像Matrix矩阵
            calcBmpMatrix();
            canvas.translate(transfrom.rect.left, transfrom.rect.top);
            canvas.clipRect(0, 0, transfrom.rect.width, transfrom.rect.height);
            canvas.concat(transMatrix);
            getDrawable().draw(canvas);
            canvas.restoreToCount(saveCount);
            if (transformStart) {
                transformStart = false;

                switch (cate) {
                    case CATE_ANIMA_TOGETHER:
                        startTogetherTrans();
                        break;
                    case CATE_ANIMA_APART:
                        startApartTrans();
                        break;
                }
            }
        } else {
            //当Transform In变化完成后，把背景改为黑色，使得 TransferImage 不透明
            paint.setAlpha(255);
            canvas.drawPaint(paint);
            super.onDraw(canvas);
        }
    }

    private void startApartTrans() {
        if (transfrom == null) return;

        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.setDuration(duration);
        valueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        if (stage == STAGE_IN_TRANSLATE) { // 平移动画
            PropertyValuesHolder leftHolder = PropertyValuesHolder.ofFloat("left", transfrom.startRect.left, transfrom.endRect.left);
            PropertyValuesHolder topHolder = PropertyValuesHolder.ofFloat("top", transfrom.startRect.top, transfrom.endRect.top);
            PropertyValuesHolder widthHolder = PropertyValuesHolder.ofFloat("width", transfrom.startRect.width, transfrom.endRect.width);
            PropertyValuesHolder heightHolder = PropertyValuesHolder.ofFloat("height", transfrom.startRect.height, transfrom.endRect.height);
            PropertyValuesHolder alphaHolder = PropertyValuesHolder.ofInt("alpha", 0, 255);
            valueAnimator.setValues(leftHolder, topHolder, widthHolder, heightHolder, alphaHolder);
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public synchronized void onAnimationUpdate(ValueAnimator animation) {
                    transfrom.rect.left = (Float) animation.getAnimatedValue("left");
                    transfrom.rect.top = (Float) animation.getAnimatedValue("top");
                    transfrom.rect.width = (Float) animation.getAnimatedValue("width");
                    transfrom.rect.height = (Float) animation.getAnimatedValue("height");
                    backgroundAlpha = (Integer) animation.getAnimatedValue("alpha");
                    invalidate();
                }
            });
        } else { // 缩放动画
            PropertyValuesHolder leftHolder = PropertyValuesHolder.ofFloat("left", transfrom.startRect.left, transfrom.endRect.left);
            PropertyValuesHolder topHolder = PropertyValuesHolder.ofFloat("top", transfrom.startRect.top, transfrom.endRect.top);
            PropertyValuesHolder widthHolder = PropertyValuesHolder.ofFloat("width", transfrom.startRect.width, transfrom.endRect.width);
            PropertyValuesHolder heightHolder = PropertyValuesHolder.ofFloat("height", transfrom.startRect.height, transfrom.endRect.height);
            PropertyValuesHolder scaleHolder = PropertyValuesHolder.ofFloat("scale", transfrom.startScale, transfrom.endScale);
            valueAnimator.setValues(scaleHolder, leftHolder, topHolder, widthHolder, heightHolder);
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public synchronized void onAnimationUpdate(ValueAnimator animation) {
                    transfrom.rect.left = (Float) animation.getAnimatedValue("left");
                    transfrom.rect.top = (Float) animation.getAnimatedValue("top");
                    transfrom.rect.width = (Float) animation.getAnimatedValue("width");
                    transfrom.rect.height = (Float) animation.getAnimatedValue("height");
                    transfrom.scale = (Float) animation.getAnimatedValue("scale");
                    invalidate();
                }
            });
        }

        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (stage == STAGE_IN_TRANSLATE) {
                    originalLocationX = (int) transfrom.endRect.left;
                    originalLocationY = (int) transfrom.endRect.top;
                    originalWidth = (int) transfrom.endRect.width;
                    originalHeight = (int) transfrom.endRect.height;
                }

                if (state == STATE_TRANS_IN && stage == STAGE_IN_SCALE)
                    TransferImage.this.state = STATE_TRANS_NORMAL;

                if (transformListener != null)
                    transformListener.onTransferComplete(state, cate, stage);

            }
        });

        valueAnimator.start();
    }

    private void startTogetherTrans() {
        if (transfrom == null) return;

        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.setDuration(duration);
        valueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        if (state == STATE_TRANS_IN) {
            PropertyValuesHolder scaleHolder = PropertyValuesHolder.ofFloat("scale", transfrom.startScale, transfrom.endScale);
            PropertyValuesHolder leftHolder = PropertyValuesHolder.ofFloat("left", transfrom.startRect.left, transfrom.endRect.left);
            PropertyValuesHolder topHolder = PropertyValuesHolder.ofFloat("top", transfrom.startRect.top, transfrom.endRect.top);
            PropertyValuesHolder widthHolder = PropertyValuesHolder.ofFloat("width", transfrom.startRect.width, transfrom.endRect.width);
            PropertyValuesHolder heightHolder = PropertyValuesHolder.ofFloat("height", transfrom.startRect.height, transfrom.endRect.height);
            PropertyValuesHolder alphaHolder = PropertyValuesHolder.ofInt("alpha", 0, 255);
            valueAnimator.setValues(scaleHolder, leftHolder, topHolder, widthHolder, heightHolder, alphaHolder);
        } else {
            PropertyValuesHolder scaleHolder = PropertyValuesHolder.ofFloat("scale", transfrom.endScale, transfrom.startScale);
            PropertyValuesHolder leftHolder = PropertyValuesHolder.ofFloat("left", transfrom.endRect.left, transfrom.startRect.left);
            PropertyValuesHolder topHolder = PropertyValuesHolder.ofFloat("top", transfrom.endRect.top, transfrom.startRect.top);
            PropertyValuesHolder widthHolder = PropertyValuesHolder.ofFloat("width", transfrom.endRect.width, transfrom.startRect.width);
            PropertyValuesHolder heightHolder = PropertyValuesHolder.ofFloat("height", transfrom.endRect.height, transfrom.startRect.height);
            PropertyValuesHolder alphaHolder = PropertyValuesHolder.ofInt("alpha", 255, 0);
            valueAnimator.setValues(scaleHolder, leftHolder, topHolder, widthHolder, heightHolder, alphaHolder);
        }

        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public synchronized void onAnimationUpdate(ValueAnimator animation) {
                transfrom.scale = (Float) animation.getAnimatedValue("scale");
                transfrom.rect.left = (Float) animation.getAnimatedValue("left");
                transfrom.rect.top = (Float) animation.getAnimatedValue("top");
                transfrom.rect.width = (Float) animation.getAnimatedValue("width");
                transfrom.rect.height = (Float) animation.getAnimatedValue("height");
                backgroundAlpha = (Integer) animation.getAnimatedValue("alpha");
                invalidate();
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (transformListener != null)
                    transformListener.onTransferComplete(state, cate, stage);

                /*
                 * 如果是进入的话，当然是希望最后停留在center_crop的区域。但是如果是out的话，就不应该是center_crop的位置了
				 * ， 而应该是最后变化的位置，因为当out的时候结束时，不回复视图是Normal，要不然会有一个突然闪动回去的bug
				 */
                if (state == STATE_TRANS_IN)
                    TransferImage.this.state = STATE_TRANS_NORMAL;

            }
        });

        valueAnimator.start();
    }

    public void setOnTransferListener(OnTransferListener listener) {
        transformListener = listener;
    }

    public interface OnTransferListener {
        /**
         * @param state  {@link #STATE_TRANS_IN} {@link #STATE_TRANS_OUT}
         * @param cate  {@link #CATE_ANIMA_TOGETHER} {@link #CATE_ANIMA_APART}
         * @param stage {@link #STAGE_IN_TRANSLATE} {@link #STAGE_IN_SCALE}
         */
        void onTransferComplete(int state, int cate, int stage);
    }

    private class Transfrom {
        float startScale;// 图片开始的缩放值
        float endScale;// 图片结束的缩放值
        float scale;// 属性ValueAnimator计算出来的值
        LocationSizeF startRect;// 开始的区域
        LocationSizeF endRect;// 结束的区域
        LocationSizeF rect;// 属性ValueAnimator计算出来的值

        void initStartIn() {
            scale = startScale;
            try {
                rect = (LocationSizeF) startRect.clone();
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }

        void initStartOut() {
            scale = endScale;
            try {
                rect = (LocationSizeF) endRect.clone();
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }

    }

    private class LocationSizeF implements Cloneable {
        float left;
        float top;
        float width;
        float height;

        @Override
        public String toString() {
            return "[left:" + left + " top:" + top + " width:" + width + " height:" + height + "]";
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

    }

}

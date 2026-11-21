package br.com.memotos.bateria;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

final class Ui {
    static final int BG=0xff0b1218, SURFACE=0xff14212a, ELEVATED=0xff21333d,
            INK=0xfff0f5f6, MUTED=0xffa7b9c3, ACCENT=0xff51d3d3, MINT=0xff69e4b4,
            AMBER=0xfff1bc74, BORDER=0xff2a3d48;
    private final Context context;
    Ui(Context context) { this.context = context; }
    int dp(int n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
    LinearLayout column() {
        var box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }
    LinearLayout row() {
        var box = new LinearLayout(context);
        box.setGravity(Gravity.CENTER_VERTICAL);
        return box;
    }
    LinearLayout panel() {
        var box = column();
        box.setBackground(shape(SURFACE,20));
        pad(box,16,16);
        return box;
    }
    void pad(View v,int x,int y) { v.setPadding(dp(x),dp(y),dp(x),dp(y)); }
    void gap(LinearLayout parent,int size) {
        parent.addView(new Space(context),parent.getOrientation()==LinearLayout.VERTICAL ?
                new LinearLayout.LayoutParams(1,dp(size)) : new LinearLayout.LayoutParams(dp(size),1));
    }
    GradientDrawable shape(int color,int radius) {
        var s = new GradientDrawable();
        s.setColor(color);
        s.setCornerRadius(dp(radius));
        return s;
    }
    Drawable ripple(int color,int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x354fd1d1),shape(color,radius),null);
    }
    TextView text(String s,int sp,int color,boolean bold) {
        var t = new TextView(context);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif",Typeface.NORMAL));
        t.setFontFeatureSettings("tnum");
        t.setIncludeFontPadding(false);
        return t;
    }
    TextView action(String s,boolean primary,Runnable fn) {
        var t=text(s,15,primary?BG:INK,true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(48));
        pad(t,14,10);
        t.setBackground(ripple(primary?ACCENT:ELEVATED,14));
        t.setOnClickListener(v->fn.run());
        t.setFocusable(true);
        return t;
    }
    ImageButton iconButton(String key,String label,Runnable fn) {
        var b=new ImageButton(context);
        b.setContentDescription(label);
        b.setImageDrawable(icon(key,INK));
        b.setBackground(ripple(SURFACE,16));
        b.setOnClickListener(v->fn.run());
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));
        return b;
    }
    void enter(View view) {
        view.animate().cancel();
        if (!ValueAnimator.areAnimatorsEnabled()) { view.setTranslationY(0); view.setAlpha(1); return; }
        view.setAlpha(.65f); view.setTranslationY(dp(5));
        view.animate().alpha(1).translationY(0).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
    }
    Drawable icon(String key,int color) { return new LineIcon(key,color,dp(24)); }
    private static final class LineIcon extends Drawable {
        final String key;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int side;
        final Path path=new Path();
        LineIcon(String key,int color,int side) {
            this.key=key; this.side=side;
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f);
            p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        }
        void line(Canvas c,float... pts) {
            path.reset(); path.moveTo(pts[0],pts[1]);
            for(int i=2;i<pts.length;i+=2) path.lineTo(pts[i],pts[i+1]);
            c.drawPath(path,p);
        }
        public void draw(Canvas c) {
            var b=getBounds(); int save=c.save();
            c.translate(b.left,b.top); c.scale(b.width()/24f,b.height()/24f);
            switch(key) {
                case "panel" -> { c.drawArc(3,4,21,22,144,252,false,p); line(c,12,13,16,8); c.drawCircle(12,13,1,p); }
                case "battery" -> {c.drawRoundRect(5,5,19,21,2,2,p); line(c,10,2,14,2); line(c,9,16,15,16);line(c,9,12,15,12);}
                case "history","clock" -> {c.drawCircle(12,12,8.5f,p);line(c,12,7,12,12,15,14);}
                case "plus" -> {line(c,5,12,19,12);line(c,12,5,12,19);}
                case "remove" -> {line(c,4,6,20,6);line(c,9,6,9,3,15,3,15,6);line(c,6,9,7,21,17,21,18,9);}
                case "back" -> {line(c,14,5,7,12,14,19);}
                case "chevron" -> line(c,9,6,15,12,9,18);
                case "more" -> {for(int x=5;x<=19;x+=7)c.drawCircle(x,12,1,p);}
                case "bluetooth" -> {line(c,7,7,17,16,11,21,11,3,17,8,7,17);}
                case "detail" -> {line(c,5,18,5,12);line(c,12,18,12,5);line(c,19,18,19,9);}
                case "edit" -> {line(c,5,19,6,14,16,4,20,8,10,18,5,19);}
                case "scan" -> {line(c,3,9,3,4,8,4);line(c,16,4,21,4,21,9);line(c,21,15,21,20,16,20);line(c,8,20,3,20,3,15);}
                case "bolt" -> line(c,14,2,5,14,11,14,10,22,19,10,13,10,14,2);
            }
            c.restoreToCount(save);
        }
        public int getIntrinsicWidth(){return side;}
        public int getIntrinsicHeight(){return side;}
        public void setAlpha(int a){p.setAlpha(a);invalidateSelf();}
        public void setColorFilter(android.graphics.ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
        public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
}

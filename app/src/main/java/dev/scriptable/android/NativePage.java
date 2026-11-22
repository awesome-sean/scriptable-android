package dev.scriptable.android;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared community UI with system typography and grouped surfaces. MIT. */
final class NativePage {
    final Activity activity;
    final int background,surface,text,muted,line,accent;
    LinearLayout body;
    NativePage(Activity activity) {
        this.activity=activity;
        boolean dark=(activity.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        try {String theme=new ScriptRepository(activity).settings().optString("theme","auto");if(!"auto".equals(theme))dark="dark".equals(theme);else if(activity.getSharedPreferences("ui-appearance",0).contains("dark"))dark=activity.getSharedPreferences("ui-appearance",0).getBoolean("dark",dark);}catch(Exception ignored){}
        activity.setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        background=dark?0xFF141414:0xFFF3F3F8;surface=dark?0xFF202020:0xFFFFFFFF;text=dark?0xFFF2F2F2:0xFF17191E;
        muted=dark?0xFFB0B0B0:0xFF626771;line=dark?0xFF3A3A3A:0xFFDFE1E7;accent=dark?0xFF9DBEFF:0xFF245BBD;
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
        activity.getWindow().setStatusBarColor(background);activity.getWindow().setNavigationBarColor(background);
        activity.getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    void open(String title,Runnable back){
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(background);
        if(android.os.Build.VERSION.SDK_INT>=35)root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;});
        LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),0,dp(16),0);
        Button previous=new Button(activity);previous.setText("‹");previous.setTextSize(30);previous.setContentDescription("返回");previous.setTextColor(text);previous.setBackgroundTintList(ColorStateList.valueOf(background));previous.setOnClickListener(v->back.run());bar.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(56)));
        TextView heading=label(title,20,false);heading.setTypeface(null,Typeface.BOLD);bar.addView(heading,new LinearLayout.LayoutParams(0,dp(56),1));heading.setGravity(Gravity.CENTER_VERTICAL);root.addView(bar);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(32));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));activity.setContentView(root);
    }
    TextView label(String value,int size,boolean secondary){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(secondary?muted:text);t.setLineSpacing(dp(3),1);return t;}
    void note(LinearLayout parent,String value){TextView t=label(value,13,true);t.setPadding(dp(4),dp(4),dp(4),dp(16));parent.addView(t);}
    LinearLayout group(){LinearLayout g=new LinearLayout(activity);g.setOrientation(LinearLayout.VERTICAL);g.setPadding(dp(16),dp(10),dp(16),dp(10));g.setBackground(rounded(surface,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(20);body.addView(g,p);return g;}
    void divider(LinearLayout parent){View v=new View(activity);v.setBackgroundColor(line);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.topMargin=dp(8);p.bottomMargin=dp(8);parent.addView(v,p);}
    Button button(LinearLayout parent,String title,boolean primary,View.OnClickListener click){Button b=new Button(activity);b.setText(title);b.setTextSize(15);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(primary?surface:accent);b.setBackgroundTintList(ColorStateList.valueOf(primary?accent:surface));b.setOnClickListener(click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(4);p.bottomMargin=dp(4);parent.addView(b,p);return b;}
    EditText field(LinearLayout parent,String title,String value){TextView t=label(title,14,true);t.setPadding(0,dp(12),0,dp(4));parent.addView(t);EditText input=new EditText(activity);input.setId(View.generateViewId());input.setText(value);input.setTextSize(16);input.setTextColor(text);input.setHintTextColor(muted);input.setSingleLine(true);input.setMinHeight(dp(48));input.setBackgroundTintList(ColorStateList.valueOf(accent));parent.addView(input,new LinearLayout.LayoutParams(-1,-2));t.setLabelFor(input.getId());return input;}
}

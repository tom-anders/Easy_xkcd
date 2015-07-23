package de.tap.easy_xkcd;

import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;

import com.tap.xkcd_reader.R;

import org.sufficientlysecure.htmltextview.HtmlTextView;


public class AboutActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        //Setup toolbar and status bar color
        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.ColorPrimaryDark));
        }

        HtmlTextView tvAbout = (HtmlTextView) findViewById(R.id.tvAbout);
        tvAbout.setHtmlFromRawResource(this, R.raw.licenses, new HtmlTextView.RemoteImageGetter());

    }


}

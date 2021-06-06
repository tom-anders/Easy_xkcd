package de.tap.easy_xkcd.fragments.whatIf;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import android.transition.Slide;
import android.transition.Transition;

import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;
import androidx.viewpager.widget.ViewPager;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;
import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.tap.easy_xkcd.Activities.BaseActivity;
import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.GlideApp;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.utils.JsonParser;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WhatIfOverviewFragment extends Fragment {

    @Bind(R.id.pager) ViewPager pager;
    @Bind(R.id.tab_layout) TabLayout tabLayout;
    @Bind(R.id.fab) FloatingActionButton fab;
    private FragmentAdapter adapter;
    public static Document doc;
    private static final String OFFLINE_WHATIF_OVERVIEW_PATH = "/what if/overview";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.whatif_pager, container, false);
        ButterKnife.bind(this, v);
        setHasOptionsMenu(true);
        final PrefHelper prefHelper = ((MainActivity) getActivity()).getPrefHelper();

        if (savedInstanceState==null) {
            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = getActivity().getTheme();
            theme.resolveAttribute(R.attr.colorPrimary, typedValue, true);
            tabLayout.setBackgroundColor(typedValue.data);
            theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
            tabLayout.setSelectedTabIndicatorColor(typedValue.data);
        }

        tabLayout.addTab(tabLayout.newTab().setText(getResources().getString(R.string.whatif_overview)));
        tabLayout.addTab(tabLayout.newTab().setText(getResources().getString(R.string.nv_favorites)));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        tabLayout.setBackgroundColor(((MainActivity) getActivity()).getThemePrefs().getPrimaryColor(false));

        Slide transition = (Slide) getEnterTransition();
        if (transition != null) {
            transition.addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(@NonNull Transition transition) { }

                @Override
                public void onTransitionEnd(@NonNull Transition transition) {
                    showOverview(prefHelper);
                }

                @Override
                public void onTransitionCancel(@NonNull Transition transition) { }

                @Override
                public void onTransitionPause(@NonNull Transition transition) { }

                @Override
                public void onTransitionResume(@NonNull Transition transition) { }
            });
        } else {
            showOverview(prefHelper); //If there was no transition, just show overview right away
        }

        pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                pager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        return v;
    }

    void showOverview(PrefHelper prefHelper) {
        if (doc == null && !prefHelper.fullOfflineWhatIf()) {
            new GetDoc().execute();
        } else {
            adapter = new FragmentAdapter(getChildFragmentManager());
            pager.setAdapter(adapter);
        }
    }

    @OnClick(R.id.fab) void onClick() {
        WhatIfFragment.getInstance().getRandom();
    }

    private class GetDoc extends AsyncTask<Void, Void, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            ((BaseActivity) getActivity()).lockRotation();
            progress = new ProgressDialog(getActivity());
            progress.setMessage(getResources().getString(R.string.loading_articles));
            progress.setIndeterminate(true);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... dummy) {
            try {
                OkHttpClient okHttpClient = JsonParser.getNewHttpClient();
                Request request = new Request.Builder()
                        .url("https://what-if.xkcd.com/archive/")
                        .build();
                Response response = okHttpClient.newCall(request).execute();
                String body = response.body().string();
                doc = Jsoup.parse(body);
                /*doc = Jsoup.connect("https://what-if.xkcd.com/archive/")
                        .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.19 Safari/537.36")
                        .get();*/
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            adapter = new FragmentAdapter(getChildFragmentManager());
            pager.setAdapter(adapter);
            ((BaseActivity) getActivity()).unlockRotation();
        }

    }

    public static class FragmentAdapter extends FragmentPagerAdapter {
        public FragmentAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Fragment getItem(int position) {
            if (position==0) {
                WhatIfFragment whatIfFragment = new WhatIfFragment();
                whatIfFragment.setEnterTransition(new Slide(Gravity.BOTTOM));
                return whatIfFragment;
            } else {
                WhatIfFavoritesFragment whatIfFragment = new WhatIfFavoritesFragment();
                whatIfFragment.setEnterTransition(new Slide(Gravity.BOTTOM));
                return whatIfFragment;
            }
        }
    }

    public abstract static class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> implements FastScrollRecyclerView.SectionedAdapter { //TODO color of fast scroller in night mode?
        public ArrayList<String> titles;
        private ArrayList<String> imgs;

        private PrefHelper prefHelper;
        private DatabaseManager databaseManager;
        private ThemePrefs themePrefs;
        private Context context;

        @NonNull
        @Override
        public String getSectionName(int position) {
            return "B";
        }

        public RVAdapter(ArrayList<String> t, ArrayList<String> i, MainActivity activity) {
            this.titles = t;
            this.imgs = i;
            this.context = activity;
            prefHelper = activity.getPrefHelper();
            databaseManager = activity.getDatabaseManager();
            themePrefs = activity.getThemePrefs();
        }

        private CircularProgressDrawable getCircularProgress() {
            CircularProgressDrawable circularProgressDrawable = new CircularProgressDrawable(context);
            circularProgressDrawable.setCenterRadius(60.0f);
            circularProgressDrawable.setStrokeWidth(5.0f);
            circularProgressDrawable.setColorSchemeColors(themePrefs.getAccentColor());
            circularProgressDrawable.start();
            return circularProgressDrawable;
        }

        @Override
        public void onBindViewHolder(final ComicViewHolder comicViewHolder, int i) {
            comicViewHolder.articleTitle.setText(titles.get(i));
            String title = titles.get(i);
            int n = WhatIfFragment.mTitles.size() - WhatIfFragment.mTitles.indexOf(title);

            comicViewHolder.articleNumber.setText(String.valueOf(n));

            int id = databaseManager.getWhatIfMissingThumbnailId(title);
            if (id != 0) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(context, id));
                return;
            }
            if (prefHelper.fullOfflineWhatIf()) {
                File offlinePath = prefHelper.getOfflinePath(context);
                File dir = new File(offlinePath.getAbsolutePath() + OFFLINE_WHATIF_OVERVIEW_PATH);
                File file = new File(dir, String.valueOf(n) + ".png");

                GlideApp.with(context)
                        .load(file)
                        .apply(new RequestOptions().placeholder(getCircularProgress()))
                        .into(comicViewHolder.thumbnail);
            } else {
                GlideApp.with(context)
                        .load(imgs.get(i))
                        .apply(new RequestOptions().placeholder(getCircularProgress()))
                        .into(comicViewHolder.thumbnail);
            }
        }

        @Override
        public int getItemCount() {
            return titles.size();
        }

        public class ComicViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView articleTitle;
            TextView articleNumber;
            ImageView thumbnail;

            ComicViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.cv);
                if (themePrefs.amoledThemeEnabled()) {
                    cv.setCardBackgroundColor(Color.BLACK);
                } else if (themePrefs.nightThemeEnabled()) {
                    cv.setCardBackgroundColor(ContextCompat.getColor(context, R.color.background_material_dark));
                }
                articleTitle = (TextView) itemView.findViewById(R.id.article_title);
                articleNumber = (TextView) itemView.findViewById(R.id.article_info);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
                if (themePrefs.invertColors(false) || themePrefs.amoledThemeEnabled())
                    thumbnail.setColorFilter(themePrefs.getNegativeColorFilter());
            }
        }
    }



    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_what_if_fragment, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        super.onCreateOptionsMenu(menu, inflater);
    }
}

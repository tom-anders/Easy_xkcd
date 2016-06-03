package de.tap.easy_xkcd.fragments.whatIf;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;

public class WhatIfOverviewFragment extends android.support.v4.app.Fragment {

    @Bind(R.id.pager) ViewPager pager;
    @Bind(R.id.tab_layout) TabLayout tabLayout;
    @Bind(R.id.fab) FloatingActionButton fab;
    private FragmentAdapter adapter;
    public static Document doc;
    private static final String OFFLINE_WHATIF_OVERVIEW_PATH = "/easy xkcd/what if/overview";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.whatif_pager, container, false);
        ButterKnife.bind(this, v);
        setHasOptionsMenu(true);
        PrefHelper prefHelper = ((MainActivity) getActivity()).getPrefHelper();

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

        if (doc == null && !prefHelper.fullOfflineWhatIf()) {
            new GetDoc().execute();
        } else {
            adapter = new FragmentAdapter(getChildFragmentManager());
            pager.setAdapter(adapter);
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

    @OnClick(R.id.fab) void onClick() {
        WhatIfFragment.getInstance().getRandom();
    }

    private class GetDoc extends AsyncTask<Void, Void, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setMessage(getResources().getString(R.string.loading_articles));
            progress.setIndeterminate(true);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... dummy) {
            try {
                doc = Jsoup.connect("https://what-if.xkcd.com/archive/")
                        .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.19 Safari/537.36")
                        .get();
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
                return new WhatIfFragment();
            } else {
                return new WhatIfFavoritesFragment();
            }
        }
    }

    public abstract static class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {
        public ArrayList<String> titles;
        private ArrayList<String> imgs;

        private PrefHelper prefHelper;
        private DatabaseManager databaseManager;
        private ThemePrefs themePrefs;
        private Context context;

        public RVAdapter(ArrayList<String> t, ArrayList<String> i, MainActivity activity) {
            this.titles = t;
            this.imgs = i;
            this.context = activity;
            prefHelper = activity.getPrefHelper();
            databaseManager = activity.getDatabaseManager();
            themePrefs = activity.getThemePrefs();
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
                File offlinePath = prefHelper.getOfflinePath();
                File dir = new File(offlinePath.getAbsolutePath() + OFFLINE_WHATIF_OVERVIEW_PATH);
                File file = new File(dir, String.valueOf(n) + ".png");

                Glide.with(context)
                        .load(file)
                        .into(comicViewHolder.thumbnail);
            } else {
                Glide.with(context)
                        .load(imgs.get(i))
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
                if (themePrefs.nightThemeEnabled())
                    cv.setCardBackgroundColor(ContextCompat.getColor(context, R.color.background_material_dark));
                articleTitle = (TextView) itemView.findViewById(R.id.article_title);
                articleNumber = (TextView) itemView.findViewById(R.id.article_info);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
                if (themePrefs.invertColors())
                    thumbnail.setColorFilter(themePrefs.getNegativeColorFilter());
            }
        }
    }



    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_what_if_fragment, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
}

package de.tap.easy_xkcd.fragments;

import android.app.ProgressDialog;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.tap.easy_xkcd.utils.PrefHelper;

public class WhatIfOverviewFragment extends android.support.v4.app.Fragment {

    @Bind(R.id.pager) ViewPager pager;
    @Bind(R.id.tab_layout) TabLayout tabLayout;
    @Bind(R.id.fab) FloatingActionButton fab;
    private FragmentAdapter adapter;
    public static Document doc;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.whatif_pager, container, false);
        ButterKnife.bind(this, v);
        setHasOptionsMenu(true);

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

        if (doc == null && !PrefHelper.fullOfflineWhatIf()) {
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        for (int i = 0; i < menu.size() - 2; i++)
            menu.getItem(i).setVisible(false);

        if (PrefHelper.hideDonate())
            menu.findItem(R.id.action_donate).setVisible(false);

        menu.findItem(R.id.action_unread).setVisible(true);
        menu.findItem(R.id.action_hide_read).setVisible(true);
        menu.findItem(R.id.action_hide_read).setChecked(PrefHelper.hideRead());

        super.onCreateOptionsMenu(menu, inflater);
    }
}

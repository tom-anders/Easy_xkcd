package de.tap.easy_xkcd.fragments.whatIf;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.Activities.WhatIfActivity;
import de.tap.easy_xkcd.utils.PrefHelper;
import jp.wasabeef.recyclerview.animators.adapters.SlideInBottomAnimationAdapter;

public class WhatIfFavoritesFragment extends android.support.v4.app.Fragment {
    public static ArrayList<String> mTitles = new ArrayList<>();
    private static ArrayList<String> mImgs = new ArrayList<>();
    private static WhatIfFavoritesFragment instance;
    @Bind(R.id.rv)
    RecyclerView rv;
    public static WhatIfFavoritesRVAdapter adapter;
    private boolean offlineMode;
    private PrefHelper prefHelper;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.recycler_layout, container, false);
        ButterKnife.bind(this, v);
        setHasOptionsMenu(true);
        prefHelper = ((MainActivity) getActivity()).getPrefHelper();

        instance = this;
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(false);

        offlineMode = prefHelper.fullOfflineWhatIf();
        new DisplayOverview().execute();

        return v;
    }

    private class DisplayOverview extends AsyncTask<Void, Void, Void> {
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
            if (!offlineMode) {
                mTitles.clear();
                mImgs.clear();

                Document doc = WhatIfOverviewFragment.doc;
                Elements titles = doc.select("h1");
                Elements imagelinks = doc.select("img.archive-image");

                for (Element title : titles)
                    mTitles.add(title.text());

                for (Element image : imagelinks)
                    mImgs.add(image.absUrl("src"));

                Collections.reverse(mTitles);
                Collections.reverse(mImgs);
            } else {
                mTitles.clear();
                mTitles = prefHelper.getWhatIfTitles();
                Collections.reverse(mTitles);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            ArrayList<String> titleFav = new ArrayList<>();
            ArrayList<String> imgFav = new ArrayList<>();
            for (int i = 0; i < mTitles.size(); i++) {
                if (prefHelper.checkWhatIfFav(mTitles.size() - i)) {
                    titleFav.add(mTitles.get(i));
                    if (!offlineMode) {
                        imgFav.add(mImgs.get(i));
                    }
                }
            }
            adapter = new WhatIfFavoritesRVAdapter(titleFav, imgFav, (MainActivity) getActivity());
            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
            slideAdapter.setInterpolator(new DecelerateInterpolator());
            rv.setAdapter(slideAdapter);
            progress.dismiss();
        }
    }

    public static WhatIfFavoritesFragment getInstance() {
        return instance;
    }

    public void updateFavorites() {
        ArrayList<String> titleFav = new ArrayList<>();
        ArrayList<String> imgFav = new ArrayList<>();
        for (int i = 0; i < mTitles.size(); i++) {
            if (prefHelper.checkWhatIfFav(mTitles.size() - i)) {
                titleFav.add(mTitles.get(i));
                if (!offlineMode) {
                    imgFav.add(mImgs.get(i));
                }
            }
        }
        adapter = new WhatIfFavoritesRVAdapter(titleFav, imgFav, (MainActivity) getActivity());
        SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
        slideAdapter.setInterpolator(new DecelerateInterpolator());
        rv.setAdapter(slideAdapter);
    }

    public class WhatIfFavoritesRVAdapter extends WhatIfOverviewFragment.RVAdapter {
        public WhatIfFavoritesRVAdapter(ArrayList<String> t, ArrayList<String> i, MainActivity activity) {
            super(t, i, activity);
        }

        @Override
        public ComicViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.whatif_overview, viewGroup, false);
            v.setOnClickListener(new CustomOnClickListener());
            v.setOnLongClickListener(new CustomOnLongClickListener());
            return new ComicViewHolder(v);
        }

    }

    private class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (!prefHelper.isOnline(getActivity()) && !offlineMode) {
                Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                return;
            }
            int pos = rv.getChildAdapterPosition(v);
            Intent intent = new Intent(getActivity(), WhatIfActivity.class);
            String title = adapter.titles.get(pos);
            int n = mTitles.size() - mTitles.indexOf(title);
            WhatIfActivity.WhatIfIndex = n;
            startActivity(intent);
            prefHelper.setLastWhatIf(n);
        }
    }


    class CustomOnLongClickListener implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            final View view = v;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setItems(R.array.whatif_card_long_click_remove, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int pos;
                    int n;
                    String title;
                    switch (which) {
                        case 0:
                            pos = rv.getChildAdapterPosition(view);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.setType("text/plain");
                            share.putExtra(Intent.EXTRA_SUBJECT, "What if: " + title);
                            share.putExtra(Intent.EXTRA_TEXT, "http://what-if.xkcd.com/" + String.valueOf(n));
                            startActivity(share);
                            break;
                        case 1:
                            pos = rv.getChildAdapterPosition(view);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://what-if.xkcd.com/" + String.valueOf(n)));
                            startActivity(intent);
                            break;
                        case 2:
                            pos = rv.getChildAdapterPosition(view);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            prefHelper.removeWhatifFav(n);
                            ArrayList<String> titleFav = new ArrayList<>();
                            ArrayList<String> imgFav = new ArrayList<>();
                            for (int i = 0; i < mTitles.size(); i++) {
                                if (prefHelper.checkWhatIfFav(mTitles.size() - i)) {
                                    titleFav.add(mTitles.get(i));
                                    if (!offlineMode)
                                        imgFav.add(mImgs.get(i));
                                }
                            }
                            adapter = new WhatIfFavoritesRVAdapter(titleFav, imgFav, (MainActivity) getActivity());
                            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
                            slideAdapter.setInterpolator(new DecelerateInterpolator());
                            rv.setAdapter(slideAdapter);
                    }
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
            return true;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.findItem(R.id.action_unread).setVisible(false);
        menu.findItem(R.id.action_hide_read).setVisible(false);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }
}

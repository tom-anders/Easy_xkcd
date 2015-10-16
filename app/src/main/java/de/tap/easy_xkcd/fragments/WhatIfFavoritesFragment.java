package de.tap.easy_xkcd.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.WhatIfActivity;
import jp.wasabeef.recyclerview.animators.adapters.SlideInBottomAnimationAdapter;

public class WhatIfFavoritesFragment extends android.support.v4.app.Fragment {
    public static ArrayList<String> mTitles = new ArrayList<>();
    private static ArrayList<String> mImgs = new ArrayList<>();
    //public static RecyclerView rv;
    private static WhatIfFavoritesFragment instance;
    @Bind(R.id.rv)
    RecyclerView rv;
    private MenuItem searchMenuItem;
    public static RVAdapter adapter;
    private boolean fullOffline;
    //private FloatingActionButton fab;
    //@Bind(R.id.fab) FloatingActionButton fab;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.whatif_recycler, container, false);
        ButterKnife.bind(this, v);
        setHasOptionsMenu(true);

        //fab = (FloatingActionButton) v.findViewById(R.id.fab);
        /*fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openRandomWhatIf();
            }
        });*/
        //rv = (RecyclerView) v.findViewById(R.id.rv);
        instance = this;
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(false);
        //rv.addOnScrollListener(new CustomOnScrollListener());

        fullOffline = PrefHelper.fullOfflineWhatIf();
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
            if (!fullOffline) {
                mTitles.clear();
                mImgs.clear();

                Document doc = WhatIfOverviewFragment.doc;
                Log.e("Info", "doc loaded");
                Elements titles = doc.select("h1");
                Elements imagelinks = doc.select("img.archive-image");

                for (Element title : titles) {
                    mTitles.add(title.text());
                }
                Log.e("Info", "titles");
                for (Element image : imagelinks) {
                    mImgs.add(image.absUrl("src"));
                }
                Log.e("Info", "imgs");

                Collections.reverse(mTitles);
                Collections.reverse(mImgs);
            } else {
                mTitles.clear();
                mTitles = PrefHelper.getWhatIfTitles();
                Collections.reverse(mTitles);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            ArrayList<String> titleFav = new ArrayList<>();
            ArrayList<String> imgFav = new ArrayList<>();
            for (int i = 0; i < mTitles.size(); i++) {
                if (PrefHelper.checkWhatIfFav(mTitles.size() - i)) {
                    titleFav.add(mTitles.get(i));
                    if (!fullOffline) {
                        imgFav.add(mImgs.get(i));
                    }
                }
            }
            adapter = new RVAdapter(titleFav, imgFav);
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
            if (PrefHelper.checkWhatIfFav(mTitles.size() - i)) {
                titleFav.add(mTitles.get(i));
                if (!fullOffline) {
                    imgFav.add(mImgs.get(i));
                }
            }
        }
        adapter = new RVAdapter(titleFav, imgFav);
        SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
        slideAdapter.setInterpolator(new DecelerateInterpolator());
        rv.setAdapter(slideAdapter);
    }

    public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {
        public ArrayList<String> titles;
        public ArrayList<String> imgs;

        @Override
        public int getItemCount() {
            return titles.size();
        }

        public RVAdapter(ArrayList<String> t, ArrayList<String> i) {
            this.titles = t;
            this.imgs = i;
        }

        @Override
        public ComicViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.whatif_overview, viewGroup, false);
            v.setOnClickListener(new CustomOnClickListener());
            v.setOnLongClickListener(new CustomOnLongClickListener());
            return new ComicViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ComicViewHolder comicViewHolder, int i) {
            comicViewHolder.articleTitle.setText(titles.get(i));

            String title = titles.get(i);
            int n = mTitles.size() - mTitles.indexOf(title);

            if (titles.get(i).equals("Jupiter Descending")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.jupiter_descending));
                return;
            }
            if (titles.get(i).equals("Jupiter Submarine")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.jupiter_submarine));
                return;
            }
            if (titles.get(i).equals("New Horizons")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.new_horizons));
                return;
            }
            if (titles.get(i).equals("Proton Earth, Electron Moon")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.proton_earth));
                return;
            }

            if (!PrefHelper.fullOfflineWhatIf()) {
                Glide.with(getActivity())
                        .load(imgs.get(i))
                        .into(comicViewHolder.thumbnail);
            } else {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd/what if/overview");
                File file = new File(dir, String.valueOf(n) + ".png");

                Glide.with(getActivity())
                        .load(file)
                        .into(comicViewHolder.thumbnail);
            }

            if (PrefHelper.invertColors()) {
                float[] colorMatrix_Negative = {
                        -1.0f, 0, 0, 0, 255, //red
                        0, -1.0f, 0, 0, 255, //green
                        0, 0, -1.0f, 0, 255, //blue
                        0, 0, 0, 1.0f, 0 //alpha
                };
                ColorFilter cf = new ColorMatrixColorFilter(colorMatrix_Negative);
                comicViewHolder.thumbnail.setColorFilter(cf);
            }
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class ComicViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            public TextView articleTitle;
            ImageView thumbnail;

            ComicViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.cv);
                if (PrefHelper.nightThemeEnabled())
                    cv.setBackgroundColor(getResources().getColor(R.color.background_material_dark));
                articleTitle = (TextView) itemView.findViewById(R.id.article_title);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            }
        }
    }

    class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (!isOnline()&&!fullOffline) {
                Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                return;
            }
            int pos = rv.getChildAdapterPosition(v);
            Intent intent = new Intent(getActivity(), WhatIfActivity.class);
            String title = adapter.titles.get(pos);
            int n = mTitles.size() - mTitles.indexOf(title);
            WhatIfActivity.WhatIfIndex = n;
            startActivity(intent);
            Log.d("index", String.valueOf(n));

            PrefHelper.setLastWhatIf(n);
        }
    }

    class CustomOnLongClickListener implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            final View view = v;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setItems(R.array.card_long_click_remove, new DialogInterface.OnClickListener() {
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
                            PrefHelper.removeWhatifFav(n);
                            ArrayList<String> titleFav = new ArrayList<>();
                            ArrayList<String> imgFav = new ArrayList<>();
                            for (int i = 0; i < mTitles.size(); i++) {
                                if (PrefHelper.checkWhatIfFav(mTitles.size() - i)) {
                                    titleFav.add(mTitles.get(i));
                                    if (!fullOffline) {
                                        imgFav.add(mImgs.get(i));
                                    }
                                }
                            }
                            adapter = new RVAdapter(titleFav, imgFav);
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

    public void updateRv() {
        if (PrefHelper.hideRead()) {
            ArrayList<String> titleUnread = new ArrayList<>();
            ArrayList<String> imgUnread = new ArrayList<>();
            for (int i = 0; i < mTitles.size(); i++) {
                if (!PrefHelper.checkRead(mTitles.size() - i)) {
                    titleUnread.add(mTitles.get(i));
                    imgUnread.add(mImgs.get(i));
                }
            }
            adapter = new RVAdapter(titleUnread, imgUnread);
            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
            slideAdapter.setInterpolator(new DecelerateInterpolator());
            rv.setAdapter(slideAdapter);
            rv.scrollToPosition(titleUnread.size() - PrefHelper.getLastWhatIf());
        } else {
            adapter = new RVAdapter(mTitles, mImgs);
            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
            slideAdapter.setInterpolator(new DecelerateInterpolator());
            rv.setAdapter(slideAdapter);
            rv.scrollToPosition(mTitles.size() - PrefHelper.getLastWhatIf());
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.findItem(R.id.action_unread).setVisible(false);
        menu.findItem(R.id.action_hide_read).setVisible(false);
        //menu.findItem(R.id.action_search).setVisible(false);


        super.onCreateOptionsMenu(menu, inflater);
    }

    private boolean isOnline() {
        //Checks if the device is currently online
        ConnectivityManager cm =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }
}

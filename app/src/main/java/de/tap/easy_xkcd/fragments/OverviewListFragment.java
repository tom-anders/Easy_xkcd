package de.tap.easy_xkcd.fragments;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.utils.PrefHelper;
import xyz.danoz.recyclerviewfastscroller.vertical.VerticalRecyclerViewFastScroller;

public class OverviewListFragment extends android.support.v4.app.Fragment {
    private static String[] titles;
    private static String[] urls;
    private ListAdapter listAdapter;
    private RVAdapter rvAdapter;
    private ListView list;
    private RecyclerView rv;
    private VerticalRecyclerViewFastScroller scroller;
    private PrefHelper prefHelper;
    private static final String BROWSER_TAG = "browser";
    private static final String OVERVIEW_TAG = "overview";

    //TODO grid

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        prefHelper = ((MainActivity) getActivity()).getPrefHelper();
        setHasOptionsMenu(true);
        View v = null;
        switch (prefHelper.getOverviewStyle()) {
            case 0:
                v = inflater.inflate(R.layout.overview_list, container, false);
                list = (ListView) v.findViewById(R.id.list);
                list.setFastScrollEnabled(true);
                break;
            case 1:
                v = inflater.inflate(R.layout.recycler_layout, container, false);
                rv = (RecyclerView) v.findViewById(R.id.rv);
                rv.setLayoutManager(new LinearLayoutManager(getActivity()));
                rv.setHasFixedSize(true);
                rv.setVerticalScrollBarEnabled(false);
                scroller = (VerticalRecyclerViewFastScroller) v.findViewById(R.id.fast_scroller);
                if (!prefHelper.overviewFav())
                    scroller.setVisibility(View.VISIBLE);
                scroller.setRecyclerView(rv);
                rv.addOnScrollListener(scroller.getOnScrollListener());
                break;
        }

        if (savedInstanceState == null) {
            new updateDatabase().execute();
        } else {
            switch (prefHelper.getOverviewStyle()) {
                case 0:
                    listAdapter = new ListAdapter();
                    list.setAdapter(listAdapter);
                    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            showComic(i);
                        }
                    });
                    break;
                case 1:
                    rvAdapter = new RVAdapter();
                    rv.setAdapter(rvAdapter);
                    break;
            }
        }
        return v;
    }

    public void showComic(final int pos) {
        android.support.v4.app.FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        android.support.v4.app.Fragment fragment = fragmentManager.findFragmentByTag(BROWSER_TAG);
        int count = 0;
        switch (prefHelper.getOverviewStyle()) {
            case 0:
                count = listAdapter.getCount();
                break;
            case 1:
                count = rvAdapter.getItemCount();
                break;
        }
        if (!prefHelper.overviewFav()) {
            if (!prefHelper.fullOfflineEnabled())
                ((ComicBrowserFragment) fragment).scrollTo(count - pos - 1, false);
            else
                ((OfflineFragment) fragment).scrollTo(count - pos - 1, false);
        } else {
            int n = Integer.parseInt(Favorites.getFavoriteList(MainActivity.getInstance())[pos]);
            if (!prefHelper.fullOfflineEnabled())
                ((ComicBrowserFragment) fragment).scrollTo(n - 1, false);
            else
                ((OfflineFragment) fragment).scrollTo(n - 1, false);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(OVERVIEW_TAG)).show(fragment).commitAllowingStateLoss();
        } else {
            Transition left = TransitionInflater.from(getActivity()).inflateTransition(android.R.transition.slide_left);
            Transition right = TransitionInflater.from(getActivity()).inflateTransition(android.R.transition.slide_right);

            OverviewListFragment.this.setExitTransition(left);

            fragment.setEnterTransition(right);

            getFragmentManager().beginTransaction()
                    .hide(fragmentManager.findFragmentByTag(OVERVIEW_TAG))
                    .show(fragment)
                    .commit();
        }

        if (prefHelper.subtitleEnabled()) {
            if (!prefHelper.fullOfflineEnabled()) {
                ComicBrowserFragment comicBrowserFragment = (ComicBrowserFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(comicBrowserFragment.sLastComicNumber));
            } else {
                OfflineFragment offlineFragment = (OfflineFragment) fragmentManager.findFragmentByTag(BROWSER_TAG);
                ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(offlineFragment.sLastComicNumber));
            }
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                switch (prefHelper.getOverviewStyle()) {
                    case 0:
                        list.setSelection(pos);
                        break;
                    case 1:
                        rv.scrollToPosition(pos);
                        break;
                }
            }
        }, 250);
    }

    private class ListAdapter extends BaseAdapter {
        private LayoutInflater inflater;

        public ListAdapter() {
            inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            if (prefHelper.overviewFav())
                return Favorites.getFavoriteList(MainActivity.getInstance()).length;
            return prefHelper.getNewest();
        }

        @Override
        public String getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            ViewHolder holder;
            if (view == null) {
                holder = new ViewHolder();
                view = inflater.inflate(R.layout.overview_item, parent, false);
                holder.textView = (TextView) view.findViewById(R.id.tv);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }
            String label;
            if (prefHelper.overviewFav()) {
                int n = Integer.parseInt(Favorites.getFavoriteList(MainActivity.getInstance())[position]);
                label = n + ": " + prefHelper.getTitle(n);
            } else {
                label = String.valueOf(getCount() - position) + " " + titles[getCount() - position - 1];
                if (prefHelper.checkComicRead(getCount() - position)) {
                    if (prefHelper.nightThemeEnabled())
                        holder.textView.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
                    else
                        holder.textView.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
                } else {
                    if (prefHelper.nightThemeEnabled())
                        holder.textView.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
                    else
                        holder.textView.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
                }
            }
            holder.textView.setText(label);
            return view;
        }
    }

    public static class ViewHolder {
        public TextView textView;
    }

    public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {

        @Override
        public int getItemCount() {

            if (prefHelper.overviewFav())
                return Favorites.getFavoriteList(MainActivity.getInstance()).length;
            return titles.length;
        }

        @Override
        public ComicViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.search_result, viewGroup, false);
            v.setOnClickListener(new CustomOnClickListener());
            return new ComicViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ComicViewHolder comicViewHolder, int i) {
            int number = getItemCount() - i;
            String title;

            if (prefHelper.overviewFav()) {
                number = Integer.parseInt(Favorites.getFavoriteList(MainActivity.getInstance())[i]);
                title = prefHelper.getTitle(number);
            } else {
                title = titles[getItemCount() - i - 1];
                if (prefHelper.checkComicRead(getItemCount() - i)) {
                    if (prefHelper.nightThemeEnabled())
                        comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
                    else
                        comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
                } else {
                    if (prefHelper.nightThemeEnabled())
                        comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
                    else
                        comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
                }
            }
            comicViewHolder.comicInfo.setText(String.valueOf(number));
            comicViewHolder.comicTitle.setText(title);

            if (!MainActivity.fullOffline) {
                Glide.with(getActivity().getApplicationContext())
                        .load(urls[number - 1])
                        .asBitmap()
                        .into(comicViewHolder.thumbnail);
            } else {
                try {
                    File sdCard = prefHelper.getOfflinePath();
                    File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                    File file = new File(dir, String.valueOf(number) + ".png");
                    Glide.with(getActivity().getApplicationContext())
                            .load(file)
                            .asBitmap()
                            .into(comicViewHolder.thumbnail);
                } catch (Exception e) {
                    Log.e("Error", "loading from external storage failed");
                    try {
                        FileInputStream fis = getActivity().openFileInput(String.valueOf(number));
                        Bitmap mBitmap = BitmapFactory.decodeStream(fis);
                        fis.close();
                        comicViewHolder.thumbnail.setImageBitmap(mBitmap);
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            }
            if (prefHelper.invertColors()) {
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
            TextView comicTitle;
            TextView comicInfo;

            ImageView thumbnail;

            ComicViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.cv);
                if (prefHelper.nightThemeEnabled())
                    cv.setCardBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_material_dark));
                comicTitle = (TextView) itemView.findViewById(R.id.comic_title);
                comicInfo = (TextView) itemView.findViewById(R.id.comic_info);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            }
        }
    }

    class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            showComic(rv.getChildAdapterPosition(v));
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        for (int i = 0; i < menu.size() - 2; i++)
            menu.getItem(i).setVisible(false);

        MenuItem item = menu.findItem(R.id.action_favorite);
        item.setVisible(true);
        if (!prefHelper.overviewFav())
            item.setIcon(R.drawable.ic_favorite_outline);
        else
            item.setIcon(R.drawable.ic_action_favorite);

        if (prefHelper.hideDonate())
            menu.findItem(R.id.action_donate).setVisible(false);

        menu.findItem(R.id.action_unread).setVisible(true);
        menu.findItem(R.id.action_overview_style).setVisible(true);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_favorite:
                if (prefHelper.overviewFav())
                    item.setIcon(R.drawable.ic_favorite_outline);
                else
                    item.setIcon(R.drawable.ic_action_favorite);
                prefHelper.setOverviewFav(!prefHelper.overviewFav());
                switch (prefHelper.getOverviewStyle()) {
                    case 0:
                        listAdapter = new ListAdapter();
                        list.setAdapter(listAdapter);
                        break;
                    case 1:
                        rvAdapter = new RVAdapter();
                        rv.setAdapter(rvAdapter);
                        if (prefHelper.overviewFav()) {
                            scroller.setVisibility(View.INVISIBLE);
                            rv.setVerticalScrollBarEnabled(true);
                        } else {
                            scroller.setVisibility(View.VISIBLE);
                            rv.setVerticalScrollBarEnabled(false);
                        }
                        break;
                }
                break;
            case R.id.action_unread:
                prefHelper.setComicsUnread();
                switch (prefHelper.getOverviewStyle()) {
                    case 0:
                        listAdapter.notifyDataSetChanged();
                        break;
                    case 1:
                        rvAdapter.notifyDataSetChanged();
                        break;
                }
                break;

            case R.id.action_overview_style:
                android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
                mDialog.setTitle(R.string.overview_style_title)
                        .setSingleChoiceItems(R.array.overview_style, prefHelper.getOverviewStyle(), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                prefHelper.setOverviewStyle(i);
                                dialogInterface.dismiss();
                                OverviewListFragment overviewListFragment = (OverviewListFragment) getActivity().getSupportFragmentManager().findFragmentByTag(OVERVIEW_TAG);
                                getActivity().getSupportFragmentManager().beginTransaction()
                                        .detach(overviewListFragment)
                                        .attach(overviewListFragment)
                                        .commit();
                            }
                        }).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void notifyAdapter(int pos) {
        switch (prefHelper.getOverviewStyle()) {
            case 0:
                listAdapter.notifyDataSetChanged();
                list.setSelection(titles.length-pos);
                break;
            case 1:
                rvAdapter.notifyDataSetChanged();
                rv.scrollToPosition(titles.length-pos);
                break;
        }
    }

    private class updateDatabase extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setTitle(getResources().getString(R.string.update_database));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (!prefHelper.databaseLoaded()) {
                InputStream is = getResources().openRawResource(R.raw.comic_titles);
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                try {
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    Log.e("error:", e.getMessage());
                }
                prefHelper.setTitles(sb.toString());
                publishProgress(15);
                Log.d("info", "titles loaded");

                is = getResources().openRawResource(R.raw.comic_trans);
                br = new BufferedReader(new InputStreamReader(is));
                sb = new StringBuilder();
                try {
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    Log.e("error:", e.getMessage());
                }
                prefHelper.setTrans(sb.toString());
                publishProgress(30);
                Log.d("info", "trans loaded");

                is = getResources().openRawResource(R.raw.comic_urls);
                br = new BufferedReader(new InputStreamReader(is));
                sb = new StringBuilder();
                try {
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    Log.e("error:", e.getMessage());
                }
                prefHelper.setUrls(sb.toString(), 1579);
                Log.d("info", "urls loaded");
                prefHelper.setDatabaseLoaded();
            }
            publishProgress(50);
            if (prefHelper.isOnline(getActivity())) {
                int newest;
                try {
                    newest = new Comic(0).getComicNumber();
                } catch (IOException e) {
                    newest = prefHelper.getNewest();
                }
                StringBuilder sbTitle = new StringBuilder();
                sbTitle.append(prefHelper.getComicTitles());
                StringBuilder sbTrans = new StringBuilder();
                sbTrans.append(prefHelper.getComicTrans());
                StringBuilder sbUrl = new StringBuilder();
                sbUrl.append(prefHelper.getComicUrls());
                String title;
                String trans;
                String url;
                Comic comic;
                for (int i = prefHelper.getHighestUrls(); i < newest; i++) {
                    try {
                        comic = new Comic(i + 1);
                        title = comic.getComicData()[0];
                        trans = comic.getTranscript();
                        url = comic.getComicData()[2];
                    } catch (IOException e) {
                        title = "";
                        trans = "";
                        url = "";
                    }
                    sbTitle.append("&&");
                    sbTitle.append(title);
                    sbUrl.append("&&");
                    sbUrl.append(url);
                    sbTrans.append("&&");
                    if (!trans.equals("")) {
                        sbTrans.append(trans);
                    } else {
                        sbTrans.append("n.a.");
                    }
                    float x = newest - prefHelper.getHighestUrls();
                    int y = i - prefHelper.getHighestUrls();
                    int p = (int) ((y / x) * 50);
                    publishProgress(p + 50);
                }
                prefHelper.setTitles(sbTitle.toString());
                prefHelper.setTrans(sbTrans.toString());
                prefHelper.setUrls(sbUrl.toString(), newest);
            }
            return null;
        }

        protected void onProgressUpdate(Integer... pro) {
            progress.setProgress(pro[0]);
        }

        @Override
        protected void onPostExecute(Void dummy) {
            titles = prefHelper.getComicTitles().split("&&");
            urls = prefHelper.getComicUrls().split("&&");
            progress.dismiss();
            switch (prefHelper.getOverviewStyle()) {
                case 0:
                    listAdapter = new ListAdapter();
                    list.setAdapter(listAdapter);
                    if (prefHelper.fullOfflineEnabled()) {
                        OfflineFragment offlineFragment = (OfflineFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                        list.setSelection(titles.length - offlineFragment.sLastComicNumber);
                    } else {
                        ComicBrowserFragment comicBrowserFragment = (ComicBrowserFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                        list.setSelection(titles.length - comicBrowserFragment.sLastComicNumber);
                    }
                    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            showComic(i);
                        }
                    });
                    break;
                case 1:
                    rvAdapter = new RVAdapter();
                    rv.setAdapter(rvAdapter);
                    if (prefHelper.fullOfflineEnabled()) {
                        OfflineFragment offlineFragment = (OfflineFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                        rv.scrollToPosition(titles.length - offlineFragment.sLastComicNumber);
                    } else {
                        ComicBrowserFragment comicBrowserFragment = (ComicBrowserFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                        rv.scrollToPosition(titles.length - comicBrowserFragment.sLastComicNumber);
                    }
                    break;
            }
        }
    }

}

package de.tap.easy_xkcd.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import java.util.Arrays;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.utils.Favorites;

public class OverviewListFragment extends OverviewBaseFragment {
    private ListAdapter listAdapter;
    private ListView list;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setupVariables();
        View v = inflater.inflate(R.layout.overview_list, container, false);
        list = (ListView) v.findViewById(R.id.list);
        list.setFastScrollEnabled(true);

        if (savedInstanceState == null) {
            new updateListDatabase().execute();
        } else {
            listAdapter = new ListAdapter();
            list.setAdapter(listAdapter);
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    showComic(i);
                }
            });
            list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    updateBookmark(i);
                    return true;
                }
            });
        }
        return v;
    }

    @Override
    protected void updateBookmark(int i) {
        int count = listAdapter.getCount();
        if (bookmark == 0)
            Toast.makeText(getActivity(), R.string.bookmark_toast_2, Toast.LENGTH_LONG).show();
        super.updateBookmark(count - i - 1);
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void showComic(final int pos) {
        int number = listAdapter.getCount() - pos - 1;
        super.showComic(number);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                list.setSelection(pos);
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
            return titles.size();
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
            int number = titles.keyAt(getCount() - position - 1);
            label = String.valueOf(number) + " " + titles.get(number);
            if (checkComicRead(number) && !prefHelper.overviewFav()) {
                if (themePrefs.nightThemeEnabled())
                    holder.textView.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
                else
                    holder.textView.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
            } else {
                if (themePrefs.nightThemeEnabled())
                    holder.textView.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
                else
                    holder.textView.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
            }
            if (number == bookmark) {
                TypedValue typedValue = new TypedValue();
                getActivity().getTheme().resolveAttribute(R.attr.colorAccent, typedValue, true);
                holder.textView.setTextColor(typedValue.data);
            }
            holder.textView.setText(label);
            return view;
        }
    }

    public static class ViewHolder {
        public TextView textView;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_boomark:
                int count = listAdapter.getCount();
                showComic(count - titles.indexOfKey(bookmark) - 1);
                break;
            case R.id.action_unread:
                prefHelper.setComicsUnread();
                setupAdapter();
                break;
            case R.id.action_favorite:
                if (prefHelper.overviewFav()) {
                    item.setIcon(R.drawable.ic_favorite_outline);
                    item.setTitle(R.string.nv_favorites);
                } else {
                    item.setIcon(R.drawable.ic_action_favorite);
                    item.setTitle(R.string.action_overview);
                }
                prefHelper.setOverviewFav(!prefHelper.overviewFav());
                getActivity().invalidateOptionsMenu();
                setupAdapter();
                break;
            case R.id.action_hide_read:
                item.setChecked(!item.isChecked());
                prefHelper.setHideRead(item.isChecked());
                setupAdapter();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_boomark).setVisible(!prefHelper.overviewFav());
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void notifyAdapter(int pos) {
        if (prefHelper == null)
            return;
        super.notifyAdapter(pos);
        if (prefHelper.hideRead())
            setupAdapter();
        else {
            listAdapter.notifyDataSetChanged();
            list.setSelection(titles.size() - pos);
        }
    }

    @Override
    protected void setupAdapter() {
        super.setupAdapter();
        ComicFragment comicFragment = (ComicFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
        listAdapter = new ListAdapter();
        list.setAdapter(listAdapter);
        if (comicFragment.lastComicNumber <= titles.size())
            list.setSelection(titles.size() - comicFragment.lastComicNumber);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                showComic(i);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                updateBookmark(i);
                return true;
            }
        });
    }

    protected class updateListDatabase extends updateDatabase {
        @Override
        protected void onPostExecute(Void dummy) {
            setupAdapter();
            super.onPostExecute(dummy);
        }
    }

}

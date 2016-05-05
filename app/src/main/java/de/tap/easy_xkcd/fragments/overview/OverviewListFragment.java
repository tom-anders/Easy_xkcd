/**
 * *******************************************************************************
 * Copyright 2016 Tom Praschan
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ******************************************************************************
 */

package de.tap.easy_xkcd.fragments.overview;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.fragments.comics.ComicFragment;

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
            databaseManager.new updateComicDatabase(null, this, prefHelper).execute();
        } else {
            super.setupAdapter();
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
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    final int number = comics.get(i).getComicNumber();
                    final boolean isRead = comics.get(i).isRead();
                    int array = isRead ? R.array.card_long_click_remove : R.array.card_long_click;
                    builder.setItems(array, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            switch (i) {
                                case 0:
                                    updateBookmark(i);
                                    break;
                                case 1:
                                    databaseManager.setRead(number, !isRead);
                                    listAdapter.notifyDataSetChanged();
                                    break;
                            }
                        }
                    }).create().show();
                    return true;
                }
            });
        }
        return v;
    }

    @Override
    protected void updateBookmark(int i) {
        if (bookmark == 0)
            Toast.makeText(getActivity(), R.string.bookmark_toast_2, Toast.LENGTH_LONG).show();
        super.updateBookmark(i);
        listAdapter.notifyDataSetChanged();
    }

    private class ListAdapter extends BaseAdapter {
        private LayoutInflater inflater;

        public ListAdapter() {
            inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return comics.size();
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
            RealmComic comic = comics.get(position);
            String label = comic.getComicNumber() + " " + comic.getTitle();
            if (comic.isRead() && !prefHelper.overviewFav()) {
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
            if (comic.getComicNumber() == bookmark) {
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
                super.goToComic(bookmark - 1);
                break;
            case R.id.action_unread:
                databaseManager.setComicsUnread();
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
    public void notifyAdapter(int number) {
        if (prefHelper == null)
            return;
        listAdapter.notifyDataSetChanged();
        if (!prefHelper.hideRead())
            list.setSelection(comics.size() - number);
        else
            list.setSelection(comics.size() - databaseManager.getNextUnread(number, comics));
    }

    @Override
    protected void setupAdapter() {
        super.setupAdapter();
        ComicFragment comicFragment = (ComicFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
        listAdapter = new ListAdapter();
        list.setAdapter(listAdapter);
        if (comicFragment.lastComicNumber <= comics.size())
            list.setSelection(comics.size() - comicFragment.lastComicNumber);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                showComic(i);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                final int number = comics.get(i).getComicNumber();
                final boolean isRead = comics.get(i).isRead();
                int array = isRead ? R.array.card_long_click_remove : R.array.card_long_click;
                builder.setItems(array, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case 0:
                                updateBookmark(i);
                                break;
                            case 1:
                                databaseManager.setRead(number, !isRead);
                                listAdapter.notifyDataSetChanged();
                                break;
                        }
                    }
                }).create().show();
                return true;
            }
        });
    }

    @Override
    public void updateDatabasePostExecute() {
        setupAdapter();
        super.updateDatabasePostExecute();
    }

}

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
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.transition.Slide;
import android.transition.TransitionInflater;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.tap.xkcd_reader.R;

import java.util.Locale;

import de.tap.easy_xkcd.database.RealmComic;
import timber.log.Timber;

public class OverviewListFragment extends OverviewBaseFragment {
    private ListAdapter listAdapter;
    private ListView list;

    private TextView lastTitle;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setupVariables();
        View v = inflater.inflate(R.layout.overview_list, container, false);
        list = (ListView) v.findViewById(R.id.list);
        list.setFastScrollEnabled(true);

        setupAdapter();
        if (savedInstanceState == null) {
            animateToolbar();

            postponeEnterTransition();
        }

        setEnterTransition(new Slide(Gravity.LEFT));
        setSharedElementEnterTransition(TransitionInflater.from(getContext()).inflateTransition(R.transition.image_shared_element_transition));

        return v;
    }



    @Override
    protected void updateBookmark(int i) {
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
            @ColorInt int accentColor = themePrefs.nightThemeEnabled() ? themePrefs.getAccentColorNight() : themePrefs.getAccentColor();
            if (view == null) {
                holder = new ViewHolder();
                view = inflater.inflate(R.layout.overview_item, parent, false);
                holder.title = view.findViewById(R.id.tv);
                holder.unreadMark = view.findViewById(R.id.unread);
                holder.background = view.findViewById(R.id.background);
                holder.unreadMark.setBackgroundColor(accentColor);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }
            RealmComic comic = comics.get(position);
            String label = String.format(Locale.ROOT, "%d: %s", comic.getComicNumber(), comic.getTitle());
            if (comic.isRead() && !prefHelper.overviewFav()) {
                holder.unreadMark.setVisibility(View.INVISIBLE);
                holder.background.setBackgroundColor(0);
            } else {
                holder.unreadMark.setVisibility(View.VISIBLE);
                @ColorInt int accentWithAlpha = accentColor & (0x1EFFFFFF);
                holder.background.setBackgroundColor(accentWithAlpha);
            }
            if (comic.getComicNumber() == bookmark) {
                TypedValue typedValue = new TypedValue();
                getActivity().getTheme().resolveAttribute(R.attr.colorAccent, typedValue, true);
                holder.title.setTextColor(typedValue.data);
            }
            holder.title.setText(label);
            holder.title.setTransitionName(String.valueOf(comic.getComicNumber()));

            if (comic.getComicNumber() == lastComicNumber) {
                lastTitle = holder.title;
                startPostponedEnterTransition();
            }
            Timber.d("loaded %d", comic.getComicNumber());
            return view;
        }
    }

    public static class ViewHolder {
        public TextView title;
        public View unreadMark;
        public View background;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_boomark:
                super.goToComic(bookmark, -1);
                break;
            case R.id.action_unread:
                databaseManager.setComicsRead(false);
                setupAdapter();
                break;
            case R.id.action_all_read:
                databaseManager.setComicsRead(true);
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
    protected TextView getCurrentTitleTextView(int position) {
        try {
            return list.getAdapter().getView(position, null, list).findViewById(R.id.tv);
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    protected ImageView getCurrentThumbnail(int number) {
        return null;
    }

    @Override
    protected void setupAdapter() {
        super.setupAdapter();
        //ComicFragment comicFragment = (ComicFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
        listAdapter = new ListAdapter();
        list.setAdapter(listAdapter);
        list.setSelection(getIndexForNumber(lastComicNumber));

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

}

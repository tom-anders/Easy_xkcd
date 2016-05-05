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

import android.content.DialogInterface;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import java.util.Arrays;

import de.tap.easy_xkcd.database.RealmComic;

public abstract class OverviewRecyclerBaseFragment extends OverviewBaseFragment {
    protected RVAdapter rvAdapter;
    protected RecyclerView rv;

    public abstract class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {

        @Override
        public int getItemCount() {
            return comics.size();
        }

        protected void setupCard(ComicViewHolder comicViewHolder, RealmComic comic, String title, int number) {
            if (comic.isRead() && !prefHelper.overviewFav()) {
                if (themePrefs.nightThemeEnabled())
                    comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
                else
                    comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
            } else {
                if (themePrefs.nightThemeEnabled())
                    comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
                else
                    comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
            }
            if (number == bookmark) {
                TypedValue typedValue = new TypedValue();
                getActivity().getTheme().resolveAttribute(R.attr.colorAccent, typedValue, true);
                comicViewHolder.comicTitle.setTextColor(typedValue.data);
            }

            comicViewHolder.comicInfo.setText(String.valueOf(number));
            comicViewHolder.comicTitle.setText(title);

            if (themePrefs.invertColors())
                comicViewHolder.thumbnail.setColorFilter(themePrefs.getNegativeColorFilter());
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
                if (themePrefs.nightThemeEnabled())
                    cv.setCardBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_material_dark));
                comicTitle = (TextView) itemView.findViewById(R.id.comic_title);
                comicInfo = (TextView) itemView.findViewById(R.id.comic_info);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            }
        }
    }

    protected class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            showComic(rv.getChildAdapterPosition(v));
        }
    }

    protected class CustomOnLongClickListener implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(final View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            final int number = comics.get(rv.getChildAdapterPosition(v)).getComicNumber();
            final boolean isRead = comics.get(rv.getChildAdapterPosition(v)).isRead();
            int array = isRead ? R.array.card_long_click_remove : R.array.card_long_click;
            builder.setItems(array, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    switch (i) {
                        case 0:
                            updateBookmark(rv.getChildAdapterPosition(v));
                            break;
                        case 1:
                            databaseManager.setRead(number, !isRead);
                            rvAdapter.notifyDataSetChanged();
                            break;
                    }
                }
            }).create().show();
            return true;
        }
    }

    @Override
    protected void updateBookmark(int i) {
        if (bookmark == 0)
            Toast.makeText(getActivity(), R.string.bookmark_toast_2, Toast.LENGTH_LONG).show();
        super.updateBookmark(i);
        rvAdapter.notifyDataSetChanged();
    }

    @Override
    public void notifyAdapter(int number) {
        if (prefHelper == null)
            return;
        rvAdapter.notifyDataSetChanged();
        if (!prefHelper.hideRead())
            rv.scrollToPosition(comics.size() - number);
        else
            rv.scrollToPosition(comics.size() - databaseManager.getNextUnread(number, comics));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
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
            case R.id.action_boomark:
                super.goToComic(bookmark - 1);
                break;
            case R.id.action_unread:
                databaseManager.setComicsUnread();
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
    protected void setupAdapter() {
        super.setupAdapter();
    }

}

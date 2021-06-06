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
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;
import com.tap.xkcd_reader.R;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import androidx.swiperefreshlayout.widget.CircularProgressDrawable;
import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.GlideApp;
import de.tap.easy_xkcd.GlideRequest;
import de.tap.easy_xkcd.database.RealmComic;
import timber.log.Timber;

public abstract class OverviewRecyclerBaseFragment extends OverviewBaseFragment {
    protected RVAdapter rvAdapter;
    protected FastScrollRecyclerView rv;

    public abstract class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> implements FastScrollRecyclerView.SectionedAdapter {

        @Override
        public int getItemCount() {
            return comics.size();
        }

        private CircularProgressDrawable getCircularProgress() {
            CircularProgressDrawable circularProgressDrawable = new CircularProgressDrawable(getActivity());
            circularProgressDrawable.setCenterRadius(60.0f);
            circularProgressDrawable.setStrokeWidth(5.0f);
            circularProgressDrawable.setColorSchemeColors(themePrefs.nightThemeEnabled() ? themePrefs.getAccentColorNight() : themePrefs.getAccentColor());
            circularProgressDrawable.start();
            return circularProgressDrawable;
        }

        protected void loadComicImage(RealmComic comic, ComicViewHolder comicViewHolder) {
            GlideRequest<Bitmap> request = GlideApp.with(OverviewRecyclerBaseFragment.this)
                    .asBitmap()
                    .apply(new RequestOptions().placeholder(getCircularProgress()))
                    .listener(new RequestListener<Bitmap>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                            if (comic.getComicNumber() == lastComicNumber) {
                                startPostponedEnterTransition();
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                            if (themePrefs.invertColors(false) && themePrefs.bitmapContainsColor(resource, comic.getComicNumber()))
                                comicViewHolder.thumbnail.clearColorFilter();

                            if (comic.getComicNumber() == lastComicNumber) {
                                startPostponedEnterTransition();
                            }
                            Timber.d("Loaded overview comic %d", comic.getComicNumber());
                            return false;
                        }
                    });

            if (!MainActivity.fullOffline) {
                request.load(comic.getUrl()).into(comicViewHolder.thumbnail);
            } else {
                File file = new File(prefHelper.getOfflinePath(getActivity()), String.valueOf(comic.getComicNumber()) + ".png");
                if (file.exists()) {
                    request.load(file).into(comicViewHolder.thumbnail);
                } else {
                    Timber.d("Offline file is not in external storage");
                    try {
                        FileInputStream fis = getActivity().openFileInput(String.valueOf(comic.getComicNumber()));
                        request.load(fis).into(comicViewHolder.thumbnail);
                    } catch (IOException e) {
                        Timber.e(e);
                    }
                }
            }
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

            comicViewHolder.comicTitle.setTransitionName(String.valueOf(number));
            comicViewHolder.thumbnail.setTransitionName("im"+String.valueOf(number));

            if (themePrefs.invertColors(false))
                comicViewHolder.thumbnail.setColorFilter(themePrefs.getNegativeColorFilter());
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        @NonNull
        @Override
        public String getSectionName(int position) {
            return String.valueOf((comics.size() - position) / 10 * 10);
        }

        public class ComicViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView comicTitle;
            TextView comicInfo;

            ImageView thumbnail;

            ComicViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView;
                if (themePrefs.amoledThemeEnabled()) {
                    cv.setCardBackgroundColor(Color.BLACK);
                } else if (themePrefs.nightThemeEnabled()) {
                    cv.setCardBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_material_dark));
                }
                comicTitle = itemView.findViewById(R.id.comic_title);
                comicInfo = itemView.findViewById(R.id.comic_info);
                thumbnail = itemView.findViewById(R.id.thumbnail);
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
        super.updateBookmark(i);
        rvAdapter.notifyDataSetChanged();

    }

    @Override
    protected TextView getCurrentTitleTextView(int position) {
        View view = rv.getLayoutManager().findViewByPosition(position);
        if (view != null) {
            return view.findViewById(R.id.comic_title);
        } else {
            return null;
        }
    }

    @Override
    protected ImageView getCurrentThumbnail(int position) {
        View view = rv.getLayoutManager().findViewByPosition(position);
        if (view != null) {
            return view.findViewById(R.id.thumbnail);
        } else {
            return null;
        }
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
                    item.setIcon(R.drawable.ic_favorite_off_24dp);
                    item.setTitle(R.string.nv_favorites);
                } else {
                    item.setIcon(R.drawable.ic_favorite_on_24dp);
                    item.setTitle(R.string.action_overview);
                }
                prefHelper.setOverviewFav(!prefHelper.overviewFav());
                MainActivity mainActivity = (MainActivity) getActivity();
                mainActivity.updateToolbarTitle();
                mainActivity.getNavView().setCheckedItem(mainActivity.currentFragmentToNavId());
                mainActivity.invalidateOptionsMenu();
                setupAdapter();
                break;
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
    protected void setupAdapter() {
        super.setupAdapter();

        rv.scrollToPosition(getIndexForNumber(lastComicNumber));
    }

}

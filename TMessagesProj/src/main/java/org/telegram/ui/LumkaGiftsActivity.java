package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LumkaConfig;
import org.telegram.messenger.LumkaGifts;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Picker for fake LumkaGram gifts. Only available in chats with users that are
 * listed as LumkaGram users, because only those see the gift the intended way.
 */
public class LumkaGiftsActivity extends BaseFragment {

    private long dialogId;
    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;
    private static final int TYPE_SHADOW = 2;

    private static class Row {
        int type;
        String title;
        String value;
        int giftIndex;

        Row(int type) {
            this.type = type;
        }
    }

    private final ArrayList<Row> rows = new ArrayList<>();

    public LumkaGiftsActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        dialogId = arguments != null ? arguments.getLong("dialog_id", 0) : 0;
        loadRows();
        return true;
    }

    private void loadRows() {
        rows.clear();
        Row header = new Row(TYPE_HEADER);
        header.title = "Подарок для пользователя LumkaGram";
        rows.add(header);

        for (int i = 0; i < LumkaGifts.EMOJI.length; i++) {
            Row row = new Row(TYPE_ITEM);
            row.title = LumkaGifts.EMOJI[i] + "  " + LumkaGifts.NAMES[i];
            row.value = "Отправить";
            row.giftIndex = i;
            rows.add(row);
        }

        rows.add(new Row(TYPE_SHADOW));
        Row note = new Row(TYPE_HEADER);
        note.title = "Подарки видны только в LumkaGram. Получатель должен быть добавлен в список мод-юзеров.";
        rows.add(note);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Подарки");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= rows.size()) {
                return;
            }
            Row row = rows.get(position);
            if (row.type == TYPE_ITEM) {
                sendGift(row.giftIndex);
            }
        });

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private void sendGift(int index) {
        if (dialogId == 0 || !LumkaConfig.isModUser(dialogId)) {
            return;
        }
        try {
            String text = LumkaGifts.build(index);
            SendMessagesHelper.getInstance(UserConfig.selectedAccount)
                    .sendMessage(SendMessagesHelper.SendMessageParams.of(text, dialogId));
            if (getParentActivity() != null) {
                org.telegram.ui.Components.BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, "Подарок отправлен")
                        .show();
            }
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
        finishFragment();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_ITEM;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HEADER: {
                    HeaderCell headerCell = new HeaderCell(context);
                    headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = headerCell;
                    break;
                }
                case TYPE_ITEM: {
                    TextSettingsCell cell = new TextSettingsCell(context);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = cell;
                    break;
                }
                default: {
                    view = new ShadowSectionCell(context);
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            switch (row.type) {
                case TYPE_HEADER: {
                    ((HeaderCell) holder.itemView).setText(row.title);
                    break;
                }
                case TYPE_ITEM: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    boolean divider = position + 1 < rows.size() && rows.get(position + 1).type == TYPE_ITEM;
                    cell.setTextAndValue(row.title, row.value, divider);
                    break;
                }
            }
        }
    }
}

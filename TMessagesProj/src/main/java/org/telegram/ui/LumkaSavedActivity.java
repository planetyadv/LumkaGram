package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LumkaDatabase;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
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
 * Shows everything LumkaGram kept locally: messages the other side deleted and
 * previous versions of edited messages. Tapping an entry opens the chat.
 */
public class LumkaSavedActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;
    private static final int TYPE_SHADOW = 2;
    private static final int TYPE_EMPTY = 3;

    private static class Row {
        int type;
        String title;
        String value;
        long did;
        int mid;

        Row(int type) {
            this.type = type;
        }
    }

    private final ArrayList<Row> rows = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        loadRows();
        return true;
    }

    private void loadRows() {
        rows.clear();
        LumkaDatabase db = LumkaDatabase.getInstance();

        ArrayList<LumkaDatabase.SavedMessage> deleted = db != null ? db.getDeletedMessages(200) : null;
        rows.add(header("Удалённые сообщения"));
        if (deleted == null || deleted.isEmpty()) {
            rows.add(empty("Пока ничего не сохранено"));
        } else {
            for (int i = 0; i < deleted.size(); i++) {
                LumkaDatabase.SavedMessage m = deleted.get(i);
                Row row = new Row(TYPE_ITEM);
                row.did = m.did;
                row.mid = m.mid;
                row.title = getDialogTitle(m.did);
                row.value = "Сообщение #" + m.mid;
                rows.add(row);
            }
        }
        rows.add(new Row(TYPE_SHADOW));

        ArrayList<LumkaDatabase.SavedMessage> edited = db != null ? db.getEditedMessages(200) : null;
        rows.add(header("Изменённые сообщения"));
        if (edited == null || edited.isEmpty()) {
            rows.add(empty("Пока ничего не сохранено"));
        } else {
            for (int i = 0; i < edited.size(); i++) {
                LumkaDatabase.SavedMessage m = edited.get(i);
                Row row = new Row(TYPE_ITEM);
                row.did = m.did;
                row.mid = m.mid;
                row.title = getDialogTitle(m.did);
                String preview = m.text != null ? m.text.replace('\n', ' ') : "";
                if (preview.length() > 80) {
                    preview = preview.substring(0, 80) + "…";
                }
                row.value = preview;
                rows.add(row);
            }
        }
    }

    private Row header(String title) {
        Row row = new Row(TYPE_HEADER);
        row.title = title;
        return row;
    }

    private Row empty(String title) {
        Row row = new Row(TYPE_EMPTY);
        row.title = title;
        return row;
    }

    private String getDialogTitle(long did) {
        try {
            MessagesController mc = MessagesController.getInstance(UserConfig.selectedAccount);
            if (did > 0) {
                TLRPC.User user = mc.getUser(did);
                if (user != null) {
                    String name = UserObject.getUserName(user);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            } else if (did < 0) {
                TLRPC.Chat chat = mc.getChat(-did);
                if (chat != null && !TextUtils.isEmpty(chat.title)) {
                    return chat.title;
                }
            }
        } catch (Exception ignore) {
        }
        return String.valueOf(did);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Сохранённые сообщения");
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
            if (row.type == TYPE_ITEM && row.did != 0) {
                Bundle args = new Bundle();
                args.putLong("dialog_id", row.did);
                presentFragment(new ChatActivity(args));
            }
        });

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
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
                case TYPE_EMPTY: {
                    TextSettingsCell cell = new TextSettingsCell(context);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
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
                case TYPE_EMPTY: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setText(row.title, true);
                    break;
                }
            }
        }
    }
}

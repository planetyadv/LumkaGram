package org.telegram.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LumkaConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Locale;

public class LumkaSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private EditText searchEditText;

    private static class Item {
        int type;
        String title;
        String subtitle;
        String key;
        boolean checked;

        Item(int type, String title, String subtitle, String key, boolean checked) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.key = key;
            this.checked = checked;
        }

        static Item header(String title) {
            return new Item(0, title, null, null, false);
        }

        static Item check(String title, String subtitle, String key, boolean checked) {
            return new Item(1, title, subtitle, key, checked);
        }

        static Item shadow() {
            return new Item(2, null, null, null, false);
        }
    }

    private final ArrayList<Item> allItems = new ArrayList<>();
    private final ArrayList<Item> displayedItems = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        loadItems();
        return true;
    }

    private void loadItems() {
        allItems.clear();

        // 1. Ghost Mode
        allItems.add(Item.header("Режим призрака"));
        allItems.add(Item.check("Не читать сообщения", "Не отправлять отметку о прочтении входящих сообщений", "ghostDontRead", LumkaConfig.ghostDontRead));
        allItems.add(Item.check("Не читать истории", "Скрывать просмотр историй контактов и каналов", "ghostDontReadStories", LumkaConfig.ghostDontReadStories));
        allItems.add(Item.check("Скрывать статус «печатает»", "Не показывать собеседнику набор текста и запись аудио", "ghostHideTyping", LumkaConfig.ghostHideTyping));
        allItems.add(Item.shadow());

        // 2. Anti-Delete & Anti-Edit
        allItems.add(Item.header("Удалённые и изменённые сообщения"));
        allItems.add(Item.check("Сохранять удалённые сообщения", "Удалённые собеседником сообщения останутся с пометкой 🗑", "saveDeletedMessages", LumkaConfig.saveDeletedMessages));
        allItems.add(Item.check("Сохранять историю изменений", "Хранить предыдущие версии изменённых сообщений", "saveEditedMessages", LumkaConfig.saveEditedMessages));
        allItems.add(Item.shadow());

        // 3. Chat Conveniences
        allItems.add(Item.header("Удобство в чатах"));
        allItems.add(Item.check("Копирование из закрытых каналов", "Разрешить выделение и копирование из каналов с запретом копирования", "allowCopyRestricted", LumkaConfig.allowCopyRestricted));
        allItems.add(Item.check("Подтверждение перед звонком", "Спрашивать подтверждение перед началом аудио/видеозвонка", "confirmCall", LumkaConfig.confirmCall));
        allItems.add(Item.check("Подтверждение стикеров и GIF", "Спрашивать подтверждение перед отправкой стикера или анимации", "confirmSendStickerOrGif", LumkaConfig.confirmSendStickerOrGif));
        allItems.add(Item.shadow());

        filterItems("");
    }

    private void filterItems(String query) {
        displayedItems.clear();
        if (query == null || query.trim().isEmpty()) {
            displayedItems.addAll(allItems);
        } else {
            String q = query.toLowerCase(Locale.ROOT).trim();
            for (Item item : allItems) {
                if (item.type == 1) {
                    if ((item.title != null && item.title.toLowerCase(Locale.ROOT).contains(q)) ||
                        (item.subtitle != null && item.subtitle.toLowerCase(Locale.ROOT).contains(q))) {
                        displayedItems.add(item);
                    }
                }
            }
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Настройки LumkaGram");
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

        searchEditText = new EditText(context);
        searchEditText.setHint("Поиск настроек...");
        searchEditText.setTextSize(15);
        searchEditText.setSingleLine(true);
        searchEditText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        searchEditText.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_windowBackgroundWhite)));
        searchEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterItems(s != null ? s.toString() : "");
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        FrameLayout searchContainer = new FrameLayout(context);
        searchContainer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        searchContainer.addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        frameLayout.addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= displayedItems.size()) return;
            Item item = displayedItems.get(position);
            if (item.type == 1) {
                item.checked = !item.checked;
                LumkaConfig.toggle(item.key, item.checked);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(item.checked);
                }
            }
        });

        FrameLayout.LayoutParams listParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        listParams.topMargin = AndroidUtilities.dp(56);
        frameLayout.addView(listView, listParams);

        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return displayedItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return displayedItems.get(position).type;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    HeaderCell headerCell = new HeaderCell(context);
                    headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = headerCell;
                    break;
                }
                case 1: {
                    TextCheckCell checkCell = new TextCheckCell(context);
                    checkCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = checkCell;
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
            Item item = displayedItems.get(position);
            switch (item.type) {
                case 0:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText(item.title);
                    break;
                case 1:
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    checkCell.setTextAndCheck(item.title, item.checked, position + 1 < displayedItems.size() && displayedItems.get(position + 1).type == 1);
                    break;
            }
        }
    }
}

package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LumkaConfig;
import org.telegram.messenger.LumkaGifts;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;

/**
 * Picker for fake LumkaGram gifts. Shows the real Telegram gift catalog (same cards, same
 * stickers as the official "Send Gift" screen), but sending does not spend any stars: the app
 * sends a text message that LumkaGram itself renders as a gift. Only available in private chats
 * with users listed as LumkaGram users.
 */
public class LumkaGiftsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_GIFT = 1;
    private static final int TYPE_INFO = 2;
    private static final int TYPE_LOADING = 3;

    private static final int COLUMNS = 3;

    private long dialogId;
    private RecyclerListView listView;
    private ListAdapter adapter;
    private final ArrayList<TL_stars.StarGift> gifts = new ArrayList<>();

    public LumkaGiftsActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        dialogId = arguments != null ? arguments.getLong("dialog_id", 0) : 0;
        StarsController.getInstance(currentAccount).loadStarGifts();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starGiftsLoaded);
        rebuild();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starGiftsLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starGiftsLoaded) {
            rebuild();
        }
    }

    private void rebuild() {
        gifts.clear();
        final StarsController controller = StarsController.getInstance(currentAccount);
        if (controller != null) {
            for (int i = 0; i < controller.gifts.size(); i++) {
                final TL_stars.StarGift gift = controller.gifts.get(i);
                if (gift instanceof TL_stars.TL_starGift && gift.sticker != null) {
                    gifts.add(gift);
                }
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LumkaGifts.CAPTION);
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
        final FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        final GridLayoutManager layoutManager = new GridLayoutManager(context, COLUMNS);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter != null && adapter.getItemViewType(position) == TYPE_GIFT ? 1 : COLUMNS;
            }
        });

        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        listView.setClipToPadding(false);
        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private void sendGift(long giftId) {
        if (dialogId == 0) {
            return;
        }
        LumkaConfig.addModUser(dialogId);
        try {
            final String text = LumkaGifts.build(giftId);
            SendMessagesHelper.getInstance(UserConfig.selectedAccount)
                    .sendMessage(SendMessagesHelper.SendMessageParams.of(text, dialogId));
            if (getParentActivity() != null) {
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, "Подарок отправлен")
                        .show();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        finishFragment();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        private boolean isLoading() {
            return gifts.isEmpty() && !StarsController.getInstance(currentAccount).giftsLoaded;
        }

        @Override
        public int getItemCount() {
            return 1 + (isLoading() ? 1 : gifts.size()) + 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return TYPE_HEADER;
            }
            if (isLoading() && position == 1) {
                return TYPE_LOADING;
            }
            if (position <= gifts.size()) {
                return TYPE_GIFT;
            }
            return TYPE_INFO;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_GIFT;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final View view;
            switch (viewType) {
                case TYPE_HEADER: {
                    final HeaderCell headerCell = new HeaderCell(context);
                    headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = headerCell;
                    break;
                }
                case TYPE_GIFT: {
                    final GiftSheet.GiftCell giftCell = new GiftSheet.GiftCell(context, currentAccount, null);
                    giftCell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    view = giftCell;
                    break;
                }
                case TYPE_LOADING: {
                    final TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = cell;
                    break;
                }
                default: {
                    view = new TextInfoPrivacyCell(context);
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final int viewType = holder.getItemViewType();
            if (viewType == TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText("FAKE GIFT");
            } else if (viewType == TYPE_GIFT) {
                final TL_stars.StarGift gift = gifts.get(position - 1);
                final GiftSheet.GiftCell giftCell = (GiftSheet.GiftCell) holder.itemView;
                giftCell.setStarsGift(gift, false, false, false, false, false);
                giftCell.setOnClickListener(v -> sendGift(gift.id));
            } else if (viewType == TYPE_LOADING) {
                ((TextInfoPrivacyCell) holder.itemView).setText("Загрузка подарков…");
            } else {
                ((TextInfoPrivacyCell) holder.itemView).setText("Подарки видны только в LumkaGram. Получатель должен быть добавлен в список мод-юзеров, звёзды не тратятся.");
            }
        }
    }
}

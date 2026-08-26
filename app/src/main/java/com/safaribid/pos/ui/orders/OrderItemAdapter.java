package com.safaribid.pos.ui.orders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.safaribid.pos.R;
import com.safaribid.pos.models.OrderItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderItemAdapter extends RecyclerView.Adapter<OrderItemAdapter.ItemViewHolder> {

    private final List<OrderItem> items = new ArrayList<>();

    public OrderItemAdapter(List<OrderItem> initialItems) {
        if (initialItems != null) {
            items.addAll(initialItems);
        }
    }

    public void updateItems(List<OrderItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_line, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        private final TextView txtName;
        private final TextView txtQty;
        private final TextView txtPrice;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtItemName);
            txtQty = itemView.findViewById(R.id.txtItemQty);
            txtPrice = itemView.findViewById(R.id.txtItemPrice);
        }

        void bind(OrderItem item) {
            if (item == null) return;

            txtName.setText(item.getProductTitle());
            txtQty.setText("x" + item.getQuantity());

            double lineTotal = item.getPrice() * item.getQuantity();
            txtPrice.setText(String.format(Locale.getDefault(), "KES %.2f", lineTotal));
        }
    }
}
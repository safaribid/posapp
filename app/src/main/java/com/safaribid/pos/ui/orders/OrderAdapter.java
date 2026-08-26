package com.safaribid.pos.ui.orders;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.safaribid.pos.R;
import com.safaribid.pos.models.Order;
import com.safaribid.pos.models.OrderItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    public interface OnOrderActionListener {
        void onOrderClick(Order order);
        void onAccept(Order order);
        void onReject(Order order);
        void onPrimaryAction(Order order);
    }

    private final List<Order> orders = new ArrayList<>();
    private final OnOrderActionListener listener;

    public OrderAdapter(OnOrderActionListener listener) {
        this.listener = listener;
    }

    public void setOrders(List<Order> newOrders) {
        orders.clear();
        if (newOrders != null) {
            orders.addAll(newOrders);
        }
        notifyDataSetChanged();
    }

    public void addOrder(Order order) {
        orders.add(0, order);
        notifyItemInserted(0);
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        holder.bind(orders.get(position));
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    class OrderViewHolder extends RecyclerView.ViewHolder {
        CardView cardOrder;
        TextView tvOrderNumber, tvStatus, tvTime, tvCustomer, tvItemsSummary, tvTotal;
        LinearLayout layoutNewOrderActions;
        Button btnReject, btnAccept, btnPrimaryAction;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            cardOrder = itemView.findViewById(R.id.cardOrder);
            tvOrderNumber = itemView.findViewById(R.id.tvOrderNumber);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvCustomer = itemView.findViewById(R.id.tvCustomer);
            tvItemsSummary = itemView.findViewById(R.id.tvItemsSummary);
            tvTotal = itemView.findViewById(R.id.tvTotal);
            layoutNewOrderActions = itemView.findViewById(R.id.layoutNewOrderActions);
            btnReject = itemView.findViewById(R.id.btnReject);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnPrimaryAction = itemView.findViewById(R.id.btnPrimaryAction);
        }

        void bind(Order order) {
            String shortId = order.getId() != null && order.getId().length() > 8
                    ? order.getId().substring(0, 8).toUpperCase()
                    : (order.getId() != null ? order.getId() : "—");

            tvOrderNumber.setText("#" + shortId);
            tvCustomer.setText(order.getCustomerDisplayName());
            tvTotal.setText(String.format(Locale.getDefault(), "KES %.0f", order.getTotalPrice()));
            tvTime.setText(formatTimeAgo(order.getCreatedAt()));
            tvItemsSummary.setText(buildItemsSummary(order));

            String statusLabel = order.getStatusLabel();
            tvStatus.setText(statusLabel);

            // New / unfulfilled orders → red style + Accept/Reject
            boolean isNewOrder = order.getStatus() == 2;

            if (isNewOrder) {
                // Highlight like the red order in the screenshot
                cardOrder.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
                tvStatus.setBackgroundColor(Color.parseColor("#FFCDD2"));
                tvStatus.setTextColor(Color.parseColor("#C62828"));

                layoutNewOrderActions.setVisibility(View.VISIBLE);
                btnPrimaryAction.setVisibility(View.GONE);

                btnAccept.setOnClickListener(v -> {
                    if (listener != null) listener.onAccept(order);
                });
                btnReject.setOnClickListener(v -> {
                    if (listener != null) listener.onReject(order);
                });
            } else {
                cardOrder.setCardBackgroundColor(Color.WHITE);
                tvStatus.setBackgroundColor(Color.parseColor("#E3F2FD"));
                tvStatus.setTextColor(Color.parseColor("#1565C0"));

                layoutNewOrderActions.setVisibility(View.GONE);
                btnPrimaryAction.setVisibility(View.VISIBLE);

                if (order.getStatus() == 3 || order.getStatus() == 4) {
                    btnPrimaryAction.setText("Complete Fulfillment");
                } else {
                    btnPrimaryAction.setText("View Details");
                }

                btnPrimaryAction.setOnClickListener(v -> {
                    if (listener != null) listener.onPrimaryAction(order);
                });
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onOrderClick(order);
            });
        }

        private String buildItemsSummary(Order order) {
            if (order.getItems() == null || order.getItems().isEmpty()) {
                return "No items";
            }
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (OrderItem item : order.getItems()) {
                if (count > 0) sb.append("\n");
                sb.append(item.getQuantity())
                        .append("x ")
                        .append(item.getProductTitle());
                count++;
                if (count >= 2) {
                    int remaining = order.getItems().size() - 2;
                    if (remaining > 0) {
                        sb.append("\n+").append(remaining).append(" more");
                    }
                    break;
                }
            }
            return sb.toString();
        }

        private String formatTimeAgo(String createdAt) {
            if (createdAt == null || createdAt.isEmpty()) return "";
            // Simple display for now – can improve later
            if (createdAt.length() >= 16) {
                return createdAt.substring(0, 16).replace("T", " ");
            }
            return createdAt;
        }
    }
}
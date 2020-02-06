package com.example.mifotodrive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class DataAdapter  extends RecyclerView.Adapter<DataAdapter.ViewHolder> {
    private ArrayList<ImageURL> imageUrls;
    private Context context;
    public DataAdapter(Context context, ArrayList<ImageURL> imageUrls) {
        this.context = context;
        this.imageUrls = imageUrls;
    }
    @Override
    public DataAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.image_layout, viewGroup, false);
        return new ViewHolder(view);
    }
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int i) {
        Glide.with(context).load(imageUrls.get(i).getImageUrl()).into(viewHolder.img);
    }
    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView img;
        public ViewHolder(View view) {
            super(view);
            img = view.findViewById(R.id.imageView);
        }
    }

    public void update(ArrayList<ImageURL> datas){
        imageUrls.clear();
        imageUrls.addAll(datas);
        notifyDataSetChanged();
    }
}

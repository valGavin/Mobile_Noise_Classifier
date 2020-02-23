package com.example.mobilenoiseclassifier;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class CustomList extends ArrayAdapter<String> {
    private final Activity activity;
    private final String[] strings;

    CustomList(@NonNull Activity activity, String[] strings) {
        super(activity, R.layout.list, strings);

        this.activity   = activity;
        this.strings    = strings;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        LayoutInflater inflater = activity.getLayoutInflater();
        View row_view       = inflater.inflate(R.layout.list, null, true);
        TextView textView   = row_view.findViewById(R.id.item);
        textView.setText(strings[position]);

        return row_view;
    }
}

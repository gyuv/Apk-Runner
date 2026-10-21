package com.example.notes;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class NotesActivity extends Activity {
    private LinearLayout list;
    private TextView count;
    private int notes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);
        list = findViewById(R.id.list);
        count = findViewById(R.id.count);
        for (String n : getResources().getStringArray(R.array.starter_notes)) addNote(n, false);
    }

    /** android:onClick="onAdd" on the Add button. */
    public void onAdd(View v) {
        EditText input = findViewById(R.id.input);
        CheckBox important = findViewById(R.id.important);
        String text = input.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, R.string.empty_note, Toast.LENGTH_SHORT).show();
            return;
        }
        addNote(text, important.isChecked());
        input.setText("");
    }

    private void addNote(String text, boolean important) {
        TextView item = (TextView) LayoutInflater.from(this).inflate(R.layout.note_item, list, false);
        item.setText(important ? "★ " + text : text);
        if (important) item.setTextColor(getResources().getColor(R.color.important));
        list.addView(item);
        notes++;
        count.setText(getString(R.string.count, notes));
    }
}

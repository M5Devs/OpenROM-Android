#!/usr/bin/env python3
"""Generates the Android layout resources for OpenROM-Android."""
import os

L = 'app/src/main/res/layout'
files = {}

files[f'{L}/fragment_convert.xml'] = '''<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/openrom_background"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingHorizontal="20dp"
        android:paddingTop="20dp"
        android:paddingBottom="32dp">

        <ImageView
            android:id="@+id/romeo"
            android:layout_width="132dp"
            android:layout_height="132dp"
            android:layout_gravity="center_horizontal"
            android:contentDescription="@string/cd_romeo"
            android:src="@drawable/romeo_idle" />

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/pick_card"
            style="@style/Widget.OpenROM.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:clickable="true"
            android:focusable="true"
            app:strokeColor="@color/openrom_outline"
            app:strokeWidth="1dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:padding="20dp">

                <ImageView
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:contentDescription="@null"
                    android:src="@drawable/ic_file"
                    app:tint="@color/openrom_primary" />

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/pick_rom_title"
                        android:textAppearance="@style/TextAppearance.OpenROM.Title" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="2dp"
                        android:text="@string/pick_rom_subtitle"
                        android:textAppearance="@style/TextAppearance.OpenROM.Body" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/file_card"
            style="@style/Widget.OpenROM.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:visibility="gone"
            app:strokeColor="@color/openrom_outline"
            app:strokeWidth="1dp"
            tools:visibility="visible">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:padding="16dp">

                <ImageView
                    android:id="@+id/file_icon"
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:contentDescription="@null"
                    android:src="@drawable/ic_file"
                    app:tint="@color/openrom_primary" />

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="14dp"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/file_name"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:ellipsize="middle"
                        android:maxLines="1"
                        android:textAppearance="@style/TextAppearance.OpenROM.Title"
                        tools:text="game.iso" />

                    <TextView
                        android:id="@+id/file_meta"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="2dp"
                        android:textAppearance="@style/TextAppearance.OpenROM.Body"
                        tools:text="PlayStation 2" />
                </LinearLayout>

                <com.google.android.material.chip.Chip
                    android:id="@+id/file_format_chip"
                    style="@style/Widget.Material3.Chip.Assist"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:clickable="false"
                    tools:text="ISO" />

                <ImageButton
                    android:id="@+id/clear_file"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:layout_marginStart="4dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="@string/clear_selection"
                    android:src="@drawable/ic_close"
                    app:tint="@color/openrom_on_surface_variant" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <TextView
            android:id="@+id/convert_to_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/convert_to"
            android:textAppearance="@style/TextAppearance.OpenROM.Title"
            android:visibility="gone"
            tools:visibility="visible" />

        <com.google.android.material.chip.ChipGroup
            android:id="@+id/target_chips"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            app:chipSpacingHorizontal="8dp"
            app:chipSpacingVertical="4dp"
            app:singleLine="false"
            app:singleSelection="true" />

        <TextView
            android:id="@+id/no_targets"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/no_targets"
            android:textAppearance="@style/TextAppearance.OpenROM.Body"
            android:visibility="gone" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="@string/output_label"
            android:textAppearance="@style/TextAppearance.OpenROM.Title" />

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/output_card"
            style="@style/Widget.OpenROM.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:clickable="true"
            android:focusable="true"
            app:strokeColor="@color/openrom_outline"
            app:strokeWidth="1dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:padding="16dp">

                <ImageView
                    android:layout_width="28dp"
                    android:layout_height="28dp"
                    android:contentDescription="@null"
                    android:src="@drawable/ic_folder"
                    app:tint="@color/openrom_primary" />

                <TextView
                    android:id="@+id/output_path"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="14dp"
                    android:layout_weight="1"
                    android:ellipsize="middle"
                    android:maxLines="2"
                    android:text="@string/output_not_set"
                    android:textAppearance="@style/TextAppearance.OpenROM.Body" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/convert_button"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:layout_marginTop="24dp"
            android:enabled="false"
            android:text="@string/convert"
            app:icon="@drawable/ic_nav_convert"
            app:iconGravity="textStart" />
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
'''

files[f'{L}/sheet_progress.xml'] = '''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/openrom_surface"
    android:orientation="vertical"
    android:padding="24dp">

    <ImageView
        android:id="@+id/progress_romeo"
        android:layout_width="88dp"
        android:layout_height="88dp"
        android:layout_gravity="center_horizontal"
        android:contentDescription="@string/cd_romeo"
        android:src="@drawable/romeo_working" />

    <TextView
        android:id="@+id/progress_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:gravity="center"
        android:textAppearance="@style/TextAppearance.OpenROM.Title"
        tools:text="Converting game.iso" />

    <com.google.android.material.progressindicator.LinearProgressIndicator
        android:id="@+id/progress_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="20dp"
        app:indicatorColor="@color/openrom_primary"
        app:trackColor="@color/openrom_surface_high" />

    <TextView
        android:id="@+id/progress_percent"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:gravity="end"
        android:textAppearance="@style/TextAppearance.OpenROM.Body"
        tools:text="67%" />

    <ScrollView
        android:id="@+id/log_scroll"
        android:layout_width="match_parent"
        android:layout_height="160dp"
        android:layout_marginTop="12dp"
        android:background="@color/openrom_background"
        android:padding="12dp">

        <TextView
            android:id="@+id/progress_log"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:fontFamily="monospace"
            android:textColor="@color/openrom_on_surface_variant"
            android:textSize="12sp"
            tools:text="> Hashing input..." />
    </ScrollView>

    <TextView
        android:id="@+id/progress_elapsed"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:textAppearance="@style/TextAppearance.OpenROM.Body"
        tools:text="Elapsed: 00:42" />

    <TextView
        android:id="@+id/progress_result"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:textAppearance="@style/TextAppearance.OpenROM.Body"
        android:visibility="gone"
        tools:text="Output: 1.4 GB" />

    <LinearLayout
        android:id="@+id/progress_actions"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:gravity="end"
        android:orientation="horizontal">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/action_secondary"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:visibility="gone"
            tools:text="Copy log" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/action_primary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/cancel" />
    </LinearLayout>
</LinearLayout>
'''

files[f'{L}/sheet_format_picker.xml'] = '''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/openrom_surface"
    android:orientation="vertical"
    android:paddingTop="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingHorizontal="24dp"
        android:paddingBottom="8dp"
        android:text="@string/format_picker_title"
        android:textAppearance="@style/TextAppearance.OpenROM.Title" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/format_list"
        android:layout_width="match_parent"
        android:layout_height="420dp"
        android:clipToPadding="false"
        android:paddingHorizontal="12dp"
        android:paddingBottom="24dp" />
</LinearLayout>
'''

files[f'{L}/item_format_option.xml'] = '''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/selectableItemBackground"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingHorizontal="12dp"
    android:paddingVertical="12dp">

    <com.google.android.material.chip.Chip
        android:id="@+id/option_chip"
        style="@style/Widget.Material3.Chip.Assist"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:clickable="false"
        tools:text="CHD" />

    <TextView
        android:id="@+id/option_preview"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_weight="1"
        android:ellipsize="middle"
        android:maxLines="1"
        android:textAppearance="@style/TextAppearance.OpenROM.Body"
        tools:text="chdman createdvd" />
</LinearLayout>
'''

files[f'{L}/item_format_header.xml'] = '''<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/header_text"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingHorizontal="12dp"
    android:paddingTop="16dp"
    android:paddingBottom="6dp"
    android:textAppearance="@style/TextAppearance.Material3.LabelLarge"
    android:textColor="@color/openrom_primary"
    tools:text="Built-in" />
'''

files[f'{L}/fragment_plugins.xml'] = '''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/openrom_background"
    android:orientation="vertical"
    android:paddingHorizontal="20dp"
    android:paddingTop="20dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/plugins_installed"
        android:textAppearance="@style/TextAppearance.OpenROM.Title" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="12dp"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/plugin_list"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:paddingBottom="12dp" />

        <LinearLayout
            android:id="@+id/plugins_empty"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:orientation="vertical">

            <ImageView
                android:layout_width="120dp"
                android:layout_height="120dp"
                android:contentDescription="@string/cd_romeo"
                android:src="@drawable/romeo_idle" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="@string/plugins_empty"
                android:textAppearance="@style/TextAppearance.OpenROM.Body" />
        </LinearLayout>

        <com.google.android.material.progressindicator.CircularProgressIndicator
            android:id="@+id/plugins_loading"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:indeterminate="true"
            android:visibility="gone"
            app:indicatorColor="@color/openrom_primary" />
    </FrameLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingBottom="20dp">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/browse_store"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginEnd="8dp"
            android:layout_weight="1"
            android:text="@string/browse_store"
            app:icon="@drawable/ic_store" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/install_from_file"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/install_from_file" />
    </LinearLayout>
</LinearLayout>
'''

files[f'{L}/item_plugin.xml'] = '''<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    style="@style/Widget.OpenROM.Card"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="10dp"
    app:strokeColor="@color/openrom_outline"
    app:strokeWidth="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="16dp">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <TextView
                    android:id="@+id/plugin_name"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:ellipsize="end"
                    android:maxLines="1"
                    android:textAppearance="@style/TextAppearance.OpenROM.Title"
                    tools:text="Xbox 360 Tools" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/plugin_badge"
                    style="@style/Widget.Material3.Chip.Assist"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="8dp"
                    android:clickable="false"
                    tools:text="Verified" />
            </LinearLayout>

            <TextView
                android:id="@+id/plugin_meta"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textAppearance="@style/TextAppearance.OpenROM.Body"
                tools:text="M5 Dev v1.2.0" />

            <TextView
                android:id="@+id/plugin_routes"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textAppearance="@style/TextAppearance.OpenROM.Body"
                android:textSize="12sp"
                tools:text="CCI to ISO" />
        </LinearLayout>

        <ImageButton
            android:id="@+id/plugin_uninstall"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/uninstall"
            android:src="@drawable/ic_delete"
            app:tint="@color/openrom_on_surface_variant" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
'''

files[f'{L}/fragment_settings.xml'] = '''<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/openrom_background">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingHorizontal="20dp"
        android:paddingTop="20dp"
        android:paddingBottom="32dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/settings_output_folder"
            android:textAppearance="@style/TextAppearance.OpenROM.Title" />

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/settings_output_card"
            style="@style/Widget.OpenROM.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:clickable="true"
            android:focusable="true"
            app:strokeColor="@color/openrom_outline"
            app:strokeWidth="1dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:padding="16dp">

                <ImageView
                    android:layout_width="28dp"
                    android:layout_height="28dp"
                    android:contentDescription="@null"
                    android:src="@drawable/ic_folder"
                    app:tint="@color/openrom_primary" />

                <TextView
                    android:id="@+id/settings_output_value"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="14dp"
                    android:layout_weight="1"
                    android:ellipsize="middle"
                    android:maxLines="2"
                    android:text="@string/output_not_set"
                    android:textAppearance="@style/TextAppearance.OpenROM.Body" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/settings_binaries"
            android:textAppearance="@style/TextAppearance.OpenROM.Title" />

        <TextView
            android:id="@+id/settings_binaries_status"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textAppearance="@style/TextAppearance.OpenROM.Body"
            tools:text="5 of 5 tools ready" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/settings_clear_binaries"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/clear_binary_cache" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="@string/clear_binary_cache_summary"
            android:textAppearance="@style/TextAppearance.OpenROM.Body"
            android:textSize="12sp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/settings_about"
            android:textAppearance="@style/TextAppearance.OpenROM.Title" />

        <com.google.android.material.card.MaterialCardView
            style="@style/Widget.OpenROM.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            app:strokeColor="@color/openrom_outline"
            app:strokeWidth="1dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:id="@+id/settings_version"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textAppearance="@style/TextAppearance.OpenROM.Body"
                    tools:text="Version 1.0.0" />

                <TextView
                    android:id="@+id/settings_github"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="10dp"
                    android:autoLink="web"
                    android:text="@string/settings_github_url"
                    android:textAppearance="@style/TextAppearance.OpenROM.Body"
                    android:textColor="@color/openrom_primary" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="10dp"
                    android:text="@string/settings_license"
                    android:textAppearance="@style/TextAppearance.OpenROM.Body" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
'''

for path, content in files.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as fh:
        fh.write(content)
    print('wrote', path)

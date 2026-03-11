/*
 * Copyright (C) 2016 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.device.DeviceSettings;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.SearchIndexableResource;
import android.provider.SearchIndexablesProvider;

import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_CLASS_NAME;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_ICON_RESID;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_ACTION;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_TARGET_CLASS;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_TARGET_PACKAGE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_RANK;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_RESID;
import static android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS;
import static android.provider.SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS;
import static android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS;

public final class ConfigPanelSearchIndexablesProvider extends SearchIndexablesProvider {

    private static final String TAG = "ConfigPanelSearchIndexablesProvider";

    private static final SearchIndexableResource[] INDEXABLE_RES = new SearchIndexableResource[]{
            new SearchIndexableResource(1, R.xml.main,
                    DeviceSettingsActivity.class.getName(),
                    R.drawable.ic_settings_device),
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryXmlResources(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_XML_RES_COLUMNS);
        // FIX: Actually populate the cursor so Android Settings can index your custom XML!
        for (SearchIndexableResource sir : INDEXABLE_RES) {
            cursor.addRow(generateResourceRef(sir));
        }
        return cursor;
    }

    private Object[] generateResourceRef(SearchIndexableResource sir) {
        Object[] ref = new Object[7];
        ref[COLUMN_INDEX_XML_RES_RANK] = sir.rank;
        ref[COLUMN_INDEX_XML_RES_RESID] = sir.xmlResId;
        ref[COLUMN_INDEX_XML_RES_CLASS_NAME] = null;
        ref[COLUMN_INDEX_XML_RES_ICON_RESID] = sir.iconResId;
        ref[COLUMN_INDEX_XML_RES_INTENT_ACTION] = "com.android.settings.action.EXTRA_SETTINGS";
        
        // PRO MOVE: Dynamically grab the package name instead of hardcoding the string. 
        // This prevents breakage if you ever rename or fork the package.
        ref[COLUMN_INDEX_XML_RES_INTENT_TARGET_PACKAGE] = getContext().getPackageName();
        
        ref[COLUMN_INDEX_XML_RES_INTENT_TARGET_CLASS] = sir.className;
        return ref;
    }

    @Override
    public Cursor queryRawData(String[] projection) {
        return new MatrixCursor(INDEXABLES_RAW_COLUMNS);
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        return new MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS);
    }
}
/*
 * Copyright (C) 2013 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.openintents.openpgp;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

public class OpenPgpSettingsHelper {
    private Context context;

    public OpenPgpSettingsHelper(Context context) {
        super();
        this.context = context;
    }

    public boolean isAvailable() {
        Intent intent = new Intent(IOpenPgpService.class.getName());
        List<ResolveInfo> resInfo = context.getPackageManager().queryIntentServices(intent, 0);
        if (!resInfo.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }
}

/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.model;

import static org.totschnig.myexpenses.provider.DatabaseConstants.*;

import org.totschnig.myexpenses.provider.TransactionProvider;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;

public class Payee extends Model {
  public long id;
  public String name;
  public Payee(long id, String name) {
    this.id = id;
    this.name = name;
  }
  public static final String[] PROJECTION = new String[] {
    KEY_ROWID,
    "name",
    "(select count(*) from " + TABLE_TRANSACTIONS + " WHERE " + KEY_PAYEEID + "=" + TABLE_PAYEES + "." + KEY_ROWID + ") AS mapped_transactions",
    "(select count(*) from " + TABLE_TEMPLATES    + " WHERE " + KEY_PAYEEID + "=" + TABLE_PAYEES + "." + KEY_ROWID + ") AS mapped_templates"
  };
  public static final Uri CONTENT_URI = TransactionProvider.PAYEES_URI;


  /**
   * check if a party exists, create it if not
   * @param name
   * @return id of the existing or the new party
   */
  public static long require(String name) {
    long id = find(name);
    return id == -1 ?
        Long.valueOf(new Payee(0,name).save().getLastPathSegment()) :
        id;
  }
  /**
   * Looks for a party with name
   * @param name
   * @return id or -1 if not found
   */
  public static long find(String name) {
    String selection = KEY_PAYEE_NAME + " = ?";
    String[] selectionArgs =new String[]{name};
    Cursor mCursor = cr().query(CONTENT_URI,
        new String[] {KEY_ROWID}, selection, selectionArgs, null);
    if (mCursor.getCount() == 0) {
      mCursor.close();
      return -1;
    } else {
      mCursor.moveToFirst();
      long result = mCursor.getLong(0);
      mCursor.close();
      return result;
    }
  }
  /**
   * @param name
   * @return id of new record, or -1, if it already exists
   */
  public static long maybeWrite(String name) {
    Uri uri = new Payee(0,name).save();
    return uri == null ? -1 : Long.valueOf(uri.getLastPathSegment());
  }
  public static boolean delete(long id) {
    return cr().delete(CONTENT_URI.buildUpon().appendPath(String.valueOf(id)).build(),
        null, null) > 0;
  }
  @Override
  public Uri save() {
    ContentValues initialValues = new ContentValues();
    initialValues.put("name", name);
    Uri uri;
    if (id == 0) {
      try {
        uri = cr().insert(CONTENT_URI, initialValues);
      } catch (SQLiteConstraintException e) {
        uri = null;
      }
    } else {
      uri = CONTENT_URI.buildUpon().appendPath(String.valueOf(id)).build();
      try {
        cr().update(CONTENT_URI.buildUpon().appendPath(String.valueOf(id)).build(),
            initialValues, null, null);
      } catch (SQLiteConstraintException e) {
        // TODO Auto-generated catch block
        uri = null;
      }
    }
    return uri;
  }
}

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

package org.totschnig.myexpenses.activity;

import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ACCOUNTID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PARENTID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.STATUS_UNCOMMITTED;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.model.*;
import org.totschnig.myexpenses.model.Account.Type;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.dialog.DialogUtils;
import org.totschnig.myexpenses.dialog.ProgressDialogFragment;
import org.totschnig.myexpenses.fragment.DbWriteFragment;
import org.totschnig.myexpenses.fragment.SplitPartList;
import org.totschnig.myexpenses.fragment.TaskExecutionFragment;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity for editing a transaction
 * @author Michael Totschnig
 */
public class ExpenseEdit extends EditActivity implements TaskExecutionFragment.TaskCallbacks,
    OnItemSelectedListener, LoaderManager.LoaderCallbacks<Cursor> {

  private Button mDateButton;
  private Button mTimeButton;
  private EditText mCommentText, mTitleText;
  private Button mCategoryButton;
  private Button mMethodButton;
  private Button mTypeButton;
  private AutoCompleteTextView mPayeeText;
  private TextView mPayeeLabel;
  public Long mRowId;
  private Long mTemplateId;
  private Account mAccount;
  private Calendar mCalendar = Calendar.getInstance();
  private final java.text.DateFormat mTitleDateFormat = java.text.DateFormat.
      getDateInstance(java.text.DateFormat.FULL);
  private Long mCatId = null;
  private Long mTransferAccount = null;
  private Long mMethodId = null;
  private String mLabel;
  private Transaction mTransaction;
  private boolean mTransferEnabled = false;

  /**
   *   transaction, transfer or split
   */
  private int mOperationType;


  static final int DATE_DIALOG_ID = 0;
  static final int TIME_DIALOG_ID = 1;
  static final int ACCOUNT_DIALOG_ID = 2;
  static final int METHOD_DIALOG_ID = 3;
  //CALCULATOR_REQUEST in super = 0
  private static final int ACTIVITY_EDIT_SPLIT = 1;
  private static final int SELECT_CATEGORY_REQUEST = 2;
  
  public static final int PAYEES_CURSOR=1;
  public static final int METHODS_CURSOR=2;
  public static final int ACCOUNTS_CURSOR=3;
  private LoaderManager mManager;
  
  String[] accountLabels ;
  Long[] accountIds ;
  private boolean mCreateNew = false;

  public enum HelpVariant {
    transaction,transfer,split,template,splitPartCategory,splitPartTransfer
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle extras = getIntent().getExtras();
    //upon orientation change stored in instance state, since new splitTransactions are immediately persisted to DB
    if ((mRowId = (savedInstanceState == null ? 0L : savedInstanceState.getLong("rowId"))) == 0L)
      mRowId = extras.getLong(DatabaseConstants.KEY_ROWID,0);
    mTemplateId = extras.getLong("template_id",0);
    mTransferEnabled = extras.getBoolean("transferEnabled",false);
    
    setContentView(R.layout.one_expense);
    changeEditTextBackground((ViewGroup)findViewById(android.R.id.content));
    mTypeButton = (Button) findViewById(R.id.TaType);
    mCommentText = (EditText) findViewById(R.id.Comment);
    mTitleText = (EditText) findViewById(R.id.Title);
    mDateButton = (Button) findViewById(R.id.Date);
    mTimeButton = (Button) findViewById(R.id.Time);
    mPayeeLabel = (TextView) findViewById(R.id.PayeeLabel);
    mPayeeText = (AutoCompleteTextView) findViewById(R.id.Payee);
    mCategoryButton = (Button) findViewById(R.id.Category);
    mMethodButton = (Button) findViewById(R.id.Method);
    mManager= getSupportLoaderManager();
    
    //1. fetch the transaction or create a new instance
    if (mRowId != 0 || mTemplateId != 0) {
      int taskId;
      Long objectId;
      if (mRowId != 0) {
        taskId = TaskExecutionFragment.TASK_INSTANTIATE_TRANSACTION;
        objectId = mRowId;
      }
      else {
        objectId = mTemplateId;
        //are we editing the template or instantiating a new one
        if (extras.getBoolean("instantiate"))
          taskId = TaskExecutionFragment.TASK_INSTANTIATE_TRANSACTION_FROM_TEMPLATE;
        else
          taskId = TaskExecutionFragment.TASK_INSTANTIATE_TEMPLATE;
      }
      FragmentManager fm = getSupportFragmentManager();
      fm.beginTransaction()
        .add(TaskExecutionFragment.newInstance(taskId,objectId, null), "ASYNC_TASK")
        .add(ProgressDialogFragment.newInstance(R.string.progress_dialog_loading),"PROGRESS")
        .commit();
    } else {
      mOperationType = extras.getInt("operationType");
      Long accountId = extras.getLong(KEY_ACCOUNTID);
      Long parentId = extras.getLong(KEY_PARENTID);
      if (extras.getBoolean("newTemplate",false))
        mTransaction = Template.getTypedNewInstance(mOperationType, accountId);
      else
        mTransaction = Transaction.getTypedNewInstance(mOperationType,accountId,parentId);
      //Split transactions are returned persisted to db and already have an id
      mRowId = mTransaction.id;
      if (mOperationType == MyExpenses.TYPE_TRANSFER) {
        mManager.initLoader(ACCOUNTS_CURSOR, null, this);
      } else {
        setup();
      }
    }
  }
  private void setup() {
    configAmountInput();
    Spinner spinner = (Spinner) findViewById(R.id.Status);
    if (getmAccount().type.equals(Type.CASH) ||
        mTransaction instanceof SplitPartCategory ||
        mTransaction instanceof SplitPartTransfer)
      spinner.setVisibility(View.GONE);
    else {
      ArrayAdapter<Transaction.CrStatus> sAdapter = new ArrayAdapter<Transaction.CrStatus>(
          DialogUtils.wrapContext1(this),
          R.layout.custom_spinner_item, android.R.id.text1,Transaction.CrStatus.values()) {
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
          View row = super.getView(position, convertView, parent);
          setColor(position,row);
          row.findViewById(android.R.id.text1).setVisibility(View.GONE);
          return row;
        }
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
          View row = super.getDropDownView(position, convertView, parent);
          setColor(position,row);
          return row;
        }
        private void setColor(int position, View row) {
          View color = row.findViewById(R.id.color1);
          color.setBackgroundColor(getItem(position).color);
          LinearLayout.LayoutParams lps = new LinearLayout.LayoutParams(20,20);
          lps.setMargins(10, 0, 0, 0);
          color.setLayoutParams(lps);
        }
      };
      sAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
      spinner.setAdapter(sAdapter);
      spinner.setSelection(mTransaction.crStatus.ordinal());
      spinner.setOnItemSelectedListener(this);
    }
    if (mTransaction instanceof Template) {
      findViewById(R.id.TitleRow).setVisibility(View.VISIBLE);
      setTitle(mTransaction.id == 0 ? R.string.menu_create_template : R.string.menu_edit_template);
      helpVariant = HelpVariant.template;
    } else if (mTransaction instanceof SplitTransaction) {
      setTitle(mTransaction.id == 0 ? R.string.menu_create_split : R.string.menu_edit_split);
      //SplitTransaction are always instantiated with status uncommitted,
      //we save them to DB as uncommitted, before working with them
      //when the split transaction is saved the split and its parts are committed
      View CategoryContainer = findViewById(R.id.CategoryRow);
      if (CategoryContainer == null)
        CategoryContainer = findViewById(R.id.Category);
      CategoryContainer.setVisibility(View.GONE);
      //add split list
      FragmentManager fm = getSupportFragmentManager();
      SplitPartList f = (SplitPartList) fm.findFragmentByTag("SPLIT_PART_LIST");
      if (f == null) {
        fm.beginTransaction()
          .add(R.id.OneExpense,SplitPartList.newInstance(mTransaction.id,mTransaction.accountId),"SPLIT_PART_LIST")
          .commit();
        fm.executePendingTransactions();
      }
      helpVariant = HelpVariant.split;
    } else {
      if (mTransaction instanceof SplitPartCategory) {
        setTitle(mTransaction.id == 0 ?
            R.string.menu_create_split_part_category : R.string.menu_edit_split_part_category  );
        helpVariant = HelpVariant.splitPartCategory;
        mTransaction.status = STATUS_UNCOMMITTED;
      }
      else if (mTransaction instanceof SplitPartTransfer) {
        setTitle(mTransaction.id == 0 ?
            R.string.menu_create_split_part_transfer : R.string.menu_edit_split_part_transfer );
        helpVariant = HelpVariant.splitPartTransfer;
        mTransaction.status = STATUS_UNCOMMITTED;
      }
      else if (mTransaction instanceof Transfer) {
        setTitle(mTransaction.id == 0 ?
            R.string.menu_create_transfer : R.string.menu_edit_transfer );
        helpVariant = HelpVariant.transfer;
      }
      else if (mTransaction instanceof Transaction) {
        setTitle(mTransaction.id == 0 ?
            R.string.menu_create_transaction : R.string.menu_edit_transaction );
        helpVariant = HelpVariant.transaction;
      }
    }

    if (mTransaction instanceof Template ||
        mTransaction instanceof SplitPartCategory ||
        mTransaction instanceof SplitPartTransfer) {
      findViewById(R.id.DateRow).setVisibility(View.GONE);
      //in portrait orientation we have a separate row for time
      View timeRow = findViewById(R.id.TimeRow);
      if (timeRow != null)
        timeRow.setVisibility(View.GONE);
    } else {
      mDateButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          showDialog(DATE_DIALOG_ID);
        }
      });

      mTimeButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          showDialog(TIME_DIALOG_ID);
        }
      });
    }
    
    if (mOperationType != MyExpenses.TYPE_TRANSFER && !(mTransaction instanceof SplitPartCategory)) {
      mManager.initLoader(PAYEES_CURSOR, null, this);
      mManager.initLoader(METHODS_CURSOR, null, this);
    } else {
      findViewById(R.id.PayeeRow).setVisibility(View.GONE);
      View MethodContainer = findViewById(R.id.MethodRow);
      //in Landscape there is no row for the method button
      if (MethodContainer == null)
        MethodContainer = findViewById(R.id.Method);
      MethodContainer.setVisibility(View.GONE);
    }

    mTypeButton.setOnClickListener(new View.OnClickListener() {

      public void onClick(View view) {
        mType = ! mType;
        //we need to empty payment method, since they are different for expenses and incomes
        mMethodId = null;
        mMethodButton.setText((CharSequence) mMethodButton.getTag());
        configureType();
        if (mOperationType != MyExpenses.TYPE_TRANSFER && !(mTransaction instanceof SplitPartCategory))
          mManager.restartLoader(METHODS_CURSOR, null, ExpenseEdit.this);
      } 
    });
    if (mOperationType == MyExpenses.TYPE_TRANSFER) {
      //if there is a label for the category input (portrait), we adjust it,
      //otherwise directly the button (landscape)
      TextView categoryLabel = (TextView) findViewById(R.id.CategoryLabel);
      if (categoryLabel != null)
        categoryLabel.setText(R.string.account);
      else
        mCategoryButton.setText(R.string.account);
    } else {
      //we store the original text of the button, since it depends on the orientation
      //and we want to restore it eventually
      mMethodButton.setTag(mMethodButton.getText());
      mMethodButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View view) {
          showDialog(METHOD_DIALOG_ID);
        }
      });
    }
    //category button and amount label are further set up in populateFields, since it depends on data
    populateFields();
  }
  @Override
  protected void configAmountInput() {
    super.configAmountInput();
    if (mTransaction instanceof SplitTransaction) {
      mAmountText.addTextChangedListener(new TextWatcher(){
        public void afterTextChanged(Editable s) {
          ((SplitPartList) getSupportFragmentManager().findFragmentByTag("SPLIT_PART_LIST")).updateBalance();
      }
      public void beforeTextChanged(CharSequence s, int start, int count, int after){}
      public void onTextChanged(CharSequence s, int start, int before, int count){}
      });
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    if (mTransaction instanceof SplitTransaction) {
      MenuInflater inflater = getSupportMenuInflater();
      inflater.inflate(R.menu.split, menu);
      if (!mTransferEnabled)
        menu.findItem(R.id.INSERT_TRANSFER_COMMAND).setVisible(false);
    } else if (!(mTransaction instanceof SplitPartCategory ||
        mTransaction instanceof SplitPartTransfer))
      menu.add(Menu.NONE, R.id.SAVE_AND_NEW_COMMAND, 0, R.string.menu_save_and_new)
        .setIcon(R.drawable.save_and_new_icon)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
    return true;
  }
  @Override
  public boolean dispatchCommand(int command, Object tag) {
    switch(command) {
    case android.R.id.home:
      if (mTransaction instanceof SplitTransaction) {
        ((SplitTransaction) mTransaction).cleanupCanceledEdit();
      }
      //handled in super
      break;
    case R.id.Confirm:
      if (mTransaction instanceof SplitTransaction &&
        !((SplitPartList) getSupportFragmentManager().findFragmentByTag("SPLIT_PART_LIST")).splitComplete()) {
          Toast.makeText(this,getString(R.string.unsplit_amount_greater_than_zero),Toast.LENGTH_SHORT).show();
          return true;
      }
      //handled in super
      break;
    case R.id.SAVE_AND_NEW_COMMAND:
      mCreateNew = true;
      saveState();
      return true;
    case R.id.INSERT_TA_COMMAND:
      createRow(MyExpenses.TYPE_TRANSACTION);
      return true;
    case R.id.INSERT_TRANSFER_COMMAND:
      createRow(MyExpenses.TYPE_TRANSFER);
      return true;
    }
    return super.dispatchCommand(command, tag);
  }
  private void createRow(int type) {
    Intent i = new Intent(this, ExpenseEdit.class);
    i.putExtra("operationType", type);
    i.putExtra(KEY_ACCOUNTID,mTransaction.accountId);
    i.putExtra(KEY_PARENTID,mTransaction.id);
    startActivityForResult(i, ACTIVITY_EDIT_SPLIT);
  }
  /**
   * calls the activity for selecting (and managing) categories
   */
  private void startSelectCategory() {
    Intent i = new Intent(this, ManageCategories.class);
    //i.putExtra(DatabaseConstants.KEY_ROWID, id);
    startActivityForResult(i, SELECT_CATEGORY_REQUEST);
  }
  /**
   * listens on changes in the date dialog and sets the date on the button
   */
  private DatePickerDialog.OnDateSetListener mDateSetListener =
    new DatePickerDialog.OnDateSetListener() {

    public void onDateSet(DatePicker view, int year, 
        int monthOfYear, int dayOfMonth) {
      mCalendar.set(year, monthOfYear, dayOfMonth);
      setDate();
    }
  };
  /**
   * listens on changes in the time dialog and sets the time on hte button
   */
  private TimePickerDialog.OnTimeSetListener mTimeSetListener =
    new TimePickerDialog.OnTimeSetListener() {
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
      mCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
      mCalendar.set(Calendar.MINUTE,minute);
      setTime();
    }
  };
  private ArrayAdapter<String>  mPayeeAdapter;
  private Cursor mMethodsCursor;
  private int otherAccountsCount = 0;
  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
    case DATE_DIALOG_ID:
      return new DatePickerDialog(this,
          mDateSetListener,
          mCalendar.get(Calendar.YEAR),
          mCalendar.get(Calendar.MONTH),
          mCalendar.get(Calendar.DAY_OF_MONTH)
      );
    case TIME_DIALOG_ID:
      return new TimePickerDialog(this,
          mTimeSetListener,
          mCalendar.get(Calendar.HOUR_OF_DAY),
          mCalendar.get(Calendar.MINUTE),
          true
      );
    case ACCOUNT_DIALOG_ID:
      return new  AlertDialog.Builder(this)
        .setTitle(R.string.dialog_title_select_account)
        .setSingleChoiceItems(accountLabels,
            java.util.Arrays.asList(accountIds).indexOf(mTransferAccount),
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
              mTransferAccount = accountIds[item];
              mLabel = accountLabels[item];
              setCategoryButton();
              dismissDialog(ACCOUNT_DIALOG_ID);
            }
          }
        ).create();
    case METHOD_DIALOG_ID:
      //TODO: check if this can really happen, and if yes provide Toast
      if (mMethodsCursor == null)
        return null;
      final String[] methodLabels = new String[mMethodsCursor.getCount()];
      final Long[] methodIds = new Long[mMethodsCursor.getCount()];
      if (mMethodsCursor.moveToFirst()) {
       int idIndex = mMethodsCursor.getColumnIndex(DatabaseConstants.KEY_ROWID);
       int labelIndex = mMethodsCursor.getColumnIndex(DatabaseConstants.KEY_LABEL);
       for (int i = 0; i < mMethodsCursor.getCount(); i++){
         methodIds[i] = mMethodsCursor.getLong(idIndex);
         methodLabels[i] = PaymentMethod.getDisplayLabel(mMethodsCursor.getString(labelIndex));
         mMethodsCursor.moveToNext();
       }
      } else {
        //TODO create resource string and fill with types
        Toast.makeText(this,getString(
              R.string.no_valid_payment_methods,
              getmAccount().type.getDisplayName(),
              getString(mType == EXPENSE ? R.string.expense : R.string.income)
            ), Toast.LENGTH_LONG).show();
        return null;
      }
      return new  AlertDialog.Builder(this)
        .setTitle(R.string.dialog_title_select_method)
        .setSingleChoiceItems(methodLabels,
            java.util.Arrays.asList(methodIds).indexOf(mMethodId), 
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int item) {
                mMethodId = methodIds[item];
                mMethodButton.setText(methodLabels[item]);
                removeDialog(METHOD_DIALOG_ID);
              }
            }
        )
        .setOnCancelListener(new DialogInterface.OnCancelListener() {
          @Override
          public void onCancel(DialogInterface dialog) {
            removeDialog(METHOD_DIALOG_ID);
          }
        })

        .create();      
    }
    return null;
  }
  
  /**
   * populates the input fields with a transaction from the database or a new one
   */
  private void populateFields() {

    if (mRowId != 0 || mTemplateId != 0) {
      //3 handle edit existing transaction or new one from template
      //3b  fill comment
      mCommentText.setText(mTransaction.comment);
      if (mOperationType != MyExpenses.TYPE_TRANSFER && !(mTransaction instanceof SplitPartCategory)) {
        mPayeeText.setText(mTransaction.payee);
        mMethodId = mTransaction.methodId;
        if (mMethodId != null) {
          mMethodButton.setText(PaymentMethod.getInstanceFromDb(mMethodId).getDisplayLabel());
        }
      }
      //3d fill label (category or account) we got from database, if we are a transfer we prefix 
      //with transfer direction
      mCatId = mTransaction.catId;
      mTransferAccount = mTransaction.transfer_account;
      mLabel =  mTransaction.label;
    } else {
      //4. handle edit new transaction
      //4a if we are a transfer, and we have only one other account
      //we point the transfer to that account
      if (mOperationType == MyExpenses.TYPE_TRANSFER && otherAccountsCount == 1) {
        mTransferAccount = accountIds[0];
        mLabel = accountLabels[0];
      }
    }
    setCategoryButton();
    //5.configure button behavior
    if (mOperationType == MyExpenses.TYPE_TRANSFER) {
      //5a if we are a transfer
      if (otherAccountsCount == 1) {
        //we disable the button, if there is only one account
        mCategoryButton.setEnabled(false);
      } else {
        //otherwise show dialog to select account
        mCategoryButton.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) {
            showDialog(ACCOUNT_DIALOG_ID);
          }
        });
      }
    } else {
      //5b if we are a transaction we start select category activity
      mCategoryButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View view) {
            startSelectCategory();
        }
      });
    }
    if (mTransaction instanceof Template)
      mTitleText.setText(((Template) mTransaction).title);
    if (!(mTransaction instanceof Template ||
        mTransaction instanceof SplitPartCategory ||
        mTransaction instanceof SplitPartTransfer))
      setDateTime(mTransaction.date);
    
    //add currency label to amount label
    TextView amountLabel = (TextView) findViewById(R.id.AmountLabel);    
    String currencySymbol;
    Account account = Account.getInstanceFromDb(mTransaction.accountId);
    currencySymbol = account.currency.getSymbol();
    if (mMinorUnitP) {
      switch (account.currency.getDefaultFractionDigits()) {
      case 2:
        currencySymbol += "¢";
        break;
      case 3:
        currencySymbol += "/1000";
      }
    }
    amountLabel.setText(getString(R.string.amount) + " ("+currencySymbol+")");
    //fill amount
    BigDecimal amount;
    if (mMinorUnitP) {
      amount = new BigDecimal(mTransaction.amount.getAmountMinor());
    } else {
      amount = mTransaction.amount.getAmountMajor();
    }
    int signum = amount.signum();
    switch(signum) {
    case -1:
      amount = amount.abs();
      break;
    case 1:
      mType = INCOME;
    }
    configureType();
    if (signum != 0)
      mAmountText.setText(nfDLocal.format(amount));
  }
  /**
   * extracts the fields from a date object for setting them on the buttons
   * @param date
   */
  private void setDateTime(Date date) {
    mCalendar.setTime(date);

    setDate();
    setTime();
  }
  /**
   * sets date on date button
   */
  private void setDate() {
    mDateButton.setText(mTitleDateFormat.format(mCalendar.getTime()));
  }
  
  /**
   * sets time on time button
   */
  private void setTime() {
    mTimeButton.setText(pad(mCalendar.get(Calendar.HOUR_OF_DAY)) + ":" + pad(mCalendar.get(Calendar.MINUTE)));
  }
  /**
   * helper for padding integer values smaller than 10 with 0
   * @param c
   * @return
   */
  private static String pad(int c) {
    if (c >= 10)
      return String.valueOf(c);
    else
      return "0" + String.valueOf(c);
  }

  /**
   * validates (is number interpretable as float in current locale,
   * is account selected for transfers) and saves
   * @return true upon success, false if validation fails
   */
  protected void saveState() {
    String title = "";
    BigDecimal amount = validateAmountInput(true);
    if (amount == null) {
      return;
    }
    if (mType == EXPENSE) {
      amount = amount.negate();
    }
    if (mMinorUnitP) {
      mTransaction.amount.setAmountMinor(amount.longValue());
    } else {
      mTransaction.amount.setAmountMajor(amount);
    }

    mTransaction.comment = mCommentText.getText().toString();
    if (mTransaction instanceof Template) {
      title = mTitleText.getText().toString();
      if (title.equals("")) {
        Toast.makeText(this, R.string.no_title_given, Toast.LENGTH_LONG).show();
        return;
      }
      ((Template) mTransaction).title = title;
    }
    if (!(mTransaction instanceof Template ||
        mTransaction instanceof SplitPartCategory ||
        mTransaction instanceof SplitPartTransfer))
      mTransaction.setDate(mCalendar.getTime());

    if (mOperationType == MyExpenses.TYPE_TRANSACTION)
      mTransaction.catId = mCatId;
    if (mOperationType != MyExpenses.TYPE_TRANSFER && !(mTransaction instanceof SplitPartCategory)) {
        mTransaction.setPayee(mPayeeText.getText().toString());
        mTransaction.methodId = mMethodId;
    }
    if (mOperationType == MyExpenses.TYPE_TRANSFER) {
      if (mTransferAccount == null) {
        Toast.makeText(this,getString(R.string.warning_select_account), Toast.LENGTH_LONG).show();
        return;
      }
      mTransaction.transfer_account = mTransferAccount;
    }
    getSupportFragmentManager().beginTransaction()
    .add(DbWriteFragment.newInstance(true), "SAVE_TASK")
    .commit();
  }
  /* (non-Javadoc)
   * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
   */
  @Override
  protected void onActivityResult(int requestCode, int resultCode, 
      Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if (requestCode == SELECT_CATEGORY_REQUEST && intent != null) {
      mCatId = intent.getLongExtra("cat_id",0);
      mLabel = intent.getStringExtra("label");
      mCategoryButton.setText(mLabel);
    }
  }
  @Override
  public void onBackPressed() {
    if (mTransaction instanceof SplitTransaction) {
      ((SplitTransaction) mTransaction).cleanupCanceledEdit();
    }
    super.onBackPressed();
  }
  /**
   * updates interface based on type (EXPENSE or INCOME)
   */
  private void configureType() {
    mTypeButton.setText(mType ? "+" : "-");
    if (mPayeeLabel != null) {
      mPayeeLabel.setText(mType ? R.string.payer : R.string.payee);
    }
    if (mTransaction instanceof SplitTransaction) {
      ((SplitPartList) getSupportFragmentManager().findFragmentByTag("SPLIT_PART_LIST")).updateBalance();
    }
    setCategoryButton();
  }
  /**
   *  for a transfer append an indicator of direction to the label on the category button 
   */
  private void setCategoryButton() {
    if (mLabel != null && mLabel.length() != 0) {
      String label = mLabel;
      if (mOperationType == MyExpenses.TYPE_TRANSFER) {
        label = (mType == EXPENSE ? MyExpenses.TRANSFER_EXPENSE  : MyExpenses.TRANSFER_INCOME) +
            label;
      }
      mCategoryButton.setText(label);
    }
  }
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putSerializable("calendar", mCalendar);
    //restored in onCreate
    if (mRowId != 0)
      outState.putLong("rowId", mRowId);
    if (mCatId != null)
      outState.putLong("catId", mCatId);
    if (mTransferAccount != null)
      outState.putLong("transferAccount",mTransferAccount);
    if (mMethodId != null)
      outState.putLong("methodId", mMethodId);
    outState.putString("label", mLabel);
  }
  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    mCalendar = (Calendar) savedInstanceState.getSerializable("calendar");
    mLabel = savedInstanceState.getString("label");
    if ((mCatId = savedInstanceState.getLong("catId")) == 0L)
      mCatId = null;
    if ((mTransferAccount = savedInstanceState.getLong("transferAccount")) == 0L)
      mTransferAccount = null;
    if ((mMethodId = savedInstanceState.getLong("methodId")) == 0L)
      mMethodId = null;
    else
      mMethodButton.setText(PaymentMethod.getInstanceFromDb(mMethodId).getDisplayLabel());
    configureType();
    if (!(mTransaction instanceof Template ||
        mTransaction instanceof SplitPartCategory ||
        mTransaction instanceof SplitPartTransfer)) {
      setDate();
      setTime();
    }
  }

  public Money getAmount() {
    if (getmAccount() == null)
      return null;
    Money result = new Money(getmAccount().currency,0L);
    BigDecimal amount = validateAmountInput(false);
    if (amount == null) {
      return result;
    }
    if (mType == EXPENSE) {
      amount = amount.negate();
    }
    if (mMinorUnitP) {
      result.setAmountMinor(amount.longValue());
    } else {
      result.setAmountMajor(amount);
    }
    return result;
  }
  @Override
  public void onPreExecute() {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void onProgressUpdate(int percent) {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void onCancelled() {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void onPostExecute(int taskId,Object o) {
    if (taskId != TaskExecutionFragment.TASK_DELETE_TRANSACTION) {
      mTransaction = (Transaction) o;
      if (mTransaction instanceof SplitTransaction)
        mOperationType = MyExpenses.TYPE_SPLIT;
      else if (mTransaction instanceof Template)
        mOperationType = ((Template) mTransaction).isTransfer ? MyExpenses.TYPE_TRANSFER : MyExpenses.TYPE_TRANSACTION;
      else
        mOperationType = mTransaction instanceof Transfer ? MyExpenses.TYPE_TRANSFER : MyExpenses.TYPE_TRANSACTION;
      if (mOperationType == MyExpenses.TYPE_TRANSFER) {
        mManager.initLoader(ACCOUNTS_CURSOR, null, this);
      } else {
        setup();
      }
      supportInvalidateOptionsMenu();
    }
    super.onPostExecute(taskId, o);
  }
  public Account getmAccount() {
    if (mAccount == null) {
      if (mTransaction == null)
        return null;
      mAccount = Account.getInstanceFromDb(mTransaction.accountId);
    }
    return mAccount;
  }
  @Override
  public void onItemSelected(AdapterView<?> parent, View view, int position,
      long id) {
    mTransaction.crStatus = (Transaction.CrStatus) parent.getItemAtPosition(position);
  }
  @Override
  public void onNothingSelected(AdapterView<?> parent) {
    // TODO Auto-generated method stub    
  }
  @Override
  public void onPostExecute(Object result) {
    if (result == null && mTransaction instanceof Template)
      //for the moment, the only case where we will not get an URI back is
      //if the unique constraint for template titles is violated
      //TODO: we should probably validate the title earlier
      Toast.makeText(this,getString(R.string.template_title_exists,((Template) mTransaction).title), Toast.LENGTH_LONG).show();
    else {
      if (mCreateNew) {
        mCreateNew = false;
        mTransaction.id = 0L;
        mRowId = 0L;
        setTitle(mOperationType == MyExpenses.TYPE_TRANSACTION ?
            R.string.menu_create_transaction : R.string.menu_create_transfer);
        mAmountText.setText("");
        Toast.makeText(this,getString(R.string.save_transaction_and_new_success),Toast.LENGTH_SHORT).show();
      } else {
        Intent intent=new Intent();
        intent.putExtra("sequence_count", (Long) result);
        setResult(RESULT_OK,intent);
        finish();
      }
    }
  }
  @Override
  public Model getObject() {
    return mTransaction;
  }
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    switch(id){
    case PAYEES_CURSOR:
      return new CursorLoader(this, TransactionProvider.PAYEES_URI, null, null, null, null);
    case METHODS_CURSOR:
      return new CursorLoader(this,
          TransactionProvider.METHODS_URI.buildUpon()
          .appendPath("typeFilter")
          .appendPath(mType == INCOME ? "1" : "-1")
          .appendPath(getmAccount().type.name())
          .build(), null, null, null, null);
    case ACCOUNTS_CURSOR:
      return new CursorLoader(this,TransactionProvider.ACCOUNTS_URI,
          new String[] {DatabaseConstants.KEY_ROWID, "label"},
          DatabaseConstants.KEY_ROWID + " != ? AND currency = ?",
          new String[] {String.valueOf(mTransaction.accountId),getmAccount().currency.getCurrencyCode()},null);
    }
    return null;
  }
  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    int id = loader.getId();
    switch(id) {
    case PAYEES_CURSOR:
      data.moveToFirst();
      if (mPayeeAdapter == null)
        mPayeeAdapter = new ArrayAdapter<String>(this,
          android.R.layout.simple_dropdown_item_1line);
      else
        mPayeeAdapter.clear();
      while(!data.isAfterLast()) {
        mPayeeAdapter.add(data.getString(data.getColumnIndex("name")));
        data.moveToNext();
      }
      mPayeeText.setAdapter(mPayeeAdapter);
      break;
    case METHODS_CURSOR:
      mMethodsCursor = data;
      if (data.getCount() == 0) {
        View MethodContainer = findViewById(R.id.MethodRow);
        if (MethodContainer == null)
          MethodContainer = findViewById(R.id.Method);
        MethodContainer.setVisibility(View.GONE);
      }
      break;
    case ACCOUNTS_CURSOR:
      if (accountLabels != null)
        return;
      otherAccountsCount = data.getCount();
      accountLabels = new String[otherAccountsCount];
      accountIds = new Long[otherAccountsCount];
      if(data.moveToFirst()){
        for (int i = 0; i < otherAccountsCount; i++){
          accountLabels[i] = data.getString(data.getColumnIndex("label"));
          accountIds[i] = data.getLong(data.getColumnIndex(DatabaseConstants.KEY_ROWID));
          data.moveToNext();
        }
      }
      setup();
      break;
    }
  }
  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    //should not be necessary to empty the autocompletetextview
  }
}

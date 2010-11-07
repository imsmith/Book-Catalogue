/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class BookEditAnthology extends ListActivity {
	
	private EditText mTitleText;
	private AutoCompleteTextView mAuthorText;
	private String bookAuthor;
	private String bookTitle;
	private Button mAdd;
	private CheckBox mSame;
	private Long mRowId;
	private Long mEditId = null;
	private int currentPosition = 0;
	private int maxPosition = 0;
	private CatalogueDBAdapter mDbHelper;
	private Cursor book;
	int anthology_num = CatalogueDBAdapter.ANTHOLOGY_NO;
	
	private static final int GONE = 8;
	private static final int DELETE_ID = Menu.FIRST;
	private static final int POPULATE = Menu.FIRST + 1;
	
	protected void getRowId() {
		/* Get any information from the extras bundle */
		Bundle extras = getIntent().getExtras();
		mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
	}
	
	protected ArrayList<String> getAuthors() {
		ArrayList<String> author_list = new ArrayList<String>();
		Cursor author_cur = mDbHelper.fetchAllAuthorsIgnoreBooks();
		startManagingCursor(author_cur);
		while (author_cur.moveToNext()) {
			String name = author_cur.getString(author_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED));
			author_list.add(name);
		}
		return author_list;
	}
	
	/**
	 * Display the edit fields page
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();
		
		mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
		if (mRowId == null) {
			getRowId();
		}
		loadPage();
	}
	
	/**
	 * Display the main manage anthology page. This has three parts. 
	 * 1. Setup the "Same Author" checkbox
	 * 2. Setup the "Add Title" fields
	 * 3. Populate the "Title List" - @see fillAnthology();
	 */
	public void loadPage() {
		setContentView(R.layout.list_anthology);
		
		book = mDbHelper.fetchBookById(mRowId);
		if (book != null) {
			book.moveToFirst();
		}
		
		bookAuthor = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED));
		bookTitle = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
		
		// Setup the same author field
		anthology_num = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
		mSame = (CheckBox) findViewById(R.id.same_author);
		if (anthology_num == CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS) {
			mSame.setChecked(false);
		} else {
			mSame.setChecked(true);
		}
		
		mSame.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				saveState();
				loadPage();
			}
		});
		
		ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getAuthors());
		mAuthorText = (AutoCompleteTextView) findViewById(R.id.add_author);
		mAuthorText.setAdapter(author_adapter);
		if (mSame.isChecked()) {
			mAuthorText.setVisibility(GONE);
		}
		mTitleText = (EditText) findViewById(R.id.add_title);
		
		mAdd = (Button) findViewById(R.id.row_add);
		mAdd.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				String title = mTitleText.getText().toString();
				String author = mAuthorText.getText().toString(); 
				if (mEditId == null) {
					if (mSame.isChecked()) {
						author = bookAuthor; 
					}
					mDbHelper.createAnthologyTitle(mRowId, author, title);
				} else {
					mDbHelper.updateAnthologyTitle(mEditId, mRowId, author, title);
					mEditId = null;
					mAdd.setText(R.string.anthology_add);
				}
				mTitleText.setText("");
				mAuthorText.setText("");
				fillAnthology(currentPosition);
				currentPosition = maxPosition;
			}
		});
		
		fillAnthology();
		
	}
	
	public void fillAnthology(int scroll_to_id) {
		fillAnthology();
		gotoTitle(scroll_to_id);
	}
	
	/**
	 * Populate the bookEditAnthology view
	 */
	public void fillAnthology() {
		int layout = R.layout.row_anthology;
		
		// Get all of the rows from the database and create the item list
		Cursor BooksCursor = mDbHelper.fetchAnthologyTitlesByBook(mRowId);
		maxPosition = BooksCursor.getCount();
		currentPosition = maxPosition;
		startManagingCursor(BooksCursor);
		String[] from = null;
		int[] to = null;
		// Create an array to specify the fields we want to display in the list
		from = new String[]{CatalogueDBAdapter.KEY_ROWID, CatalogueDBAdapter.KEY_POSITION, CatalogueDBAdapter.KEY_AUTHOR, CatalogueDBAdapter.KEY_TITLE};
		// and an array of the fields we want to bind those fields to (in this case just text1)
		to = new int[]{R.id.row_row_id, R.id.row_position, R.id.row_author, R.id.row_title};
		// Now create a simple cursor adapter and set it to display
		SimpleCursorAdapter books = new AnthologyTitleListAdapter(this, layout, BooksCursor, from, to);
		setListAdapter(books);
		
		registerForContextMenu(getListView());
	}
	
	/**
	 * The adapter for the Titles List
	 * 
	 * @author evan
	 */
	public class AnthologyTitleListAdapter extends SimpleCursorAdapter {
		boolean series = false;
		
		/**
		 * 
		 * Pass the parameters directly to the overridden function
		 * 
		 * @param context
		 * @param layout
		 * @param cursor
		 * @param from
		 * @param to
		 */
		public AnthologyTitleListAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to) {
			super(context, layout, cursor, from, to);
		}
		
		/**
		 * Override the setTextView function. This helps us set the appropriate opening and
		 * closing brackets for author names.
		 */
		@Override
		public void setViewText(TextView v, String text) {
			if (v.getId() == R.id.row_author) {
				if (mSame.isChecked()) {
					v.setVisibility(GONE);
				} else {
					text = " (" + text + ")";
				}
			} else if (v.getId() == R.id.row_position) {
				text = text + ". ";
			} else if (v.getId() == R.id.row_row_id) {
				final long this_text = Long.parseLong(text); 
				ImageView up = (ImageView) ((ViewGroup) v.getParent()).findViewById(R.id.row_up);
				up.setOnClickListener(new View.OnClickListener() {
					public void onClick(View view) {
						int position = mDbHelper.updateAnthologyTitlePosition(this_text, true);
						fillAnthology(position-2);
					}
				});
				ImageView down = (ImageView) ((ViewGroup) v.getParent()).findViewById(R.id.row_down);
				down.setOnClickListener(new View.OnClickListener() {
					public void onClick(View view) {
						int position = mDbHelper.updateAnthologyTitlePosition(this_text, false);
						fillAnthology(position);
					}
				});
				text = "";
			}
			v.setText(text);
		}
		
	}
	
	/**
	 * Scroll to the current group
	 */
	public void gotoTitle(int id) {
		try {
			ListView view = this.getListView();
			view.setSelection(id);
		} catch (Exception e) {
			//do nothing
		}
		return;
	}
	
	public void searchWikipedia() {
		String basepath = "http://en.wikipedia.org";
		String pathAuthor = bookAuthor.replace(" ", "+");
		pathAuthor = pathAuthor.replace(",", "");
		// Strip everything past the , from the title
		String pathTitle = bookTitle;
		int comma = bookTitle.indexOf(",");
		if (comma > 0) {
			pathTitle = pathTitle.substring(0, comma);
		}
		pathTitle = pathTitle.replace(" ", "+");
		String path = basepath + "/w/index.php?title=Special:Search&search=%22" + pathTitle + "%22+" + pathAuthor + "";
		boolean success = false;
		URL url;
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchWikipediaHandler handler = new SearchWikipediaHandler();
		SearchWikipediaEntryHandler entryHandler = new SearchWikipediaEntryHandler();

		try {
			url = new URL(path);
			parser = factory.newSAXParser();
			try {
				parser.parse(getInputStream(url), handler);
			} catch (RuntimeException e) {
				Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
				//Log.e("Book Catalogue", "SAX Runtime Exception " + e);
				return;
			}
			String[] links = handler.getLinks();
			for (int i = 0; i < links.length; i++) {
				if (links[i].equals("") || success == true) {
					break;
				}
				url = new URL(basepath + links[i]);
				parser = factory.newSAXParser();
				try {
					parser.parse(getInputStream(url), entryHandler);
					ArrayList<String> titles = entryHandler.getList();
					/* Display the confirm dialog */
					if (titles.size() > 0) {
						success = true;
						showAnthologyConfirm(titles);
					}
				} catch (RuntimeException e) {
					//Log.e("Book Catalogue", "SAX Runtime Exception " + e);
					Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
				}
			}
			if (success == false) {
				//Log.e("BC", "Fail 3");
				Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
				return;
			}
		} catch (MalformedURLException e) {
			Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			//Log.e("Book Catalogue", "Malformed URL " + e.getMessage());
		} catch (ParserConfigurationException e) {
			Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			//Log.e("Book Catalogue", "SAX Parsing Error " + e.getMessage());
		} catch (SAXException e) {
			Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			//Log.e("Book Catalogue", "SAX Exception " + e.getMessage());
		} catch (Exception e) {
			Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			//Log.e("Book Catalogue", "SAX IO Exception " + e.getMessage());
		}
		fillAnthology();
		return;
	}
	
	private void showAnthologyConfirm(final ArrayList<String> titles) {
		String anthology_title = "";
		for (int j=0; j < titles.size(); j++) {
			anthology_title += "* " + titles.get(j) + "\n";
		}
		
		AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(anthology_title).create();
		alertDialog.setTitle(R.string.anthology_confirm);
		alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
		alertDialog.setButton(this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				for (int j=0; j < titles.size(); j++) {
					String anthology_title = titles.get(j);
					anthology_title = anthology_title + ", ";
					//Log.e("BC", anthology_title);
					String anthology_author = bookAuthor;
					// Does the string look like "Hindsight by Jack Williamson"
					int pos = anthology_title.indexOf(" by ");
					if (pos > 0) {
						anthology_author = anthology_title.substring(pos+4);
						anthology_title = anthology_title.substring(0, pos);
					}
					// Trim extraneous punctionaction and whitespace from the titles and authors
					anthology_author = anthology_author.trim().replace("\n", " ").replaceAll("[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$", "").trim();
					anthology_title = anthology_title.trim().replace("\n", " ").replaceAll("[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$", "").trim();
					mDbHelper.createAnthologyTitle(mRowId, anthology_author, anthology_title);
					//Log.e("BC", anthology_author + " " + anthology_title);
				}
				fillAnthology();
				return;
			}
		}); 
		alertDialog.setButton2(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				//do nothing
				return;
			}
		}); 
		alertDialog.show();

	}
	
	protected InputStream getInputStream(URL url) {
		try {
			return url.openConnection().getInputStream();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Cursor anthology = mDbHelper.fetchAnthologyTitleById(id);
		anthology.moveToFirst();
		String title = anthology.getString(anthology.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)); 
		String author = anthology.getString(anthology.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR));
		
		currentPosition = position;
		mEditId = id;
		mTitleText.setText(title);
		mAuthorText.setText(author);
		mAdd.setText(R.string.anthology_save);
	}
	
	/**
	 * Run each time the menu button is pressed. This will setup the options menu
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		MenuItem populate = menu.add(0, POPULATE, 0, R.string.populate_anthology_titles);
		populate.setIcon(android.R.drawable.ic_menu_add);
		return super.onPrepareOptionsMenu(menu);
	}
	
	/**
	 * This will be called when a menu item is selected. A large switch statement to
	 * call the appropriate functions (or other activities) 
	 */
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch(item.getItemId()) {
		case POPULATE:
			searchWikipedia();
		}
		return super.onMenuItemSelected(featureId, item);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, DELETE_ID, 0, R.string.menu_delete_anthology);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case DELETE_ID:
				AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
				mDbHelper.deleteAnthologyTitle(info.id);
				fillAnthology();
				return true;
		}
		return super.onContextItemSelected(item);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mRowId != null) {
			outState.putLong(CatalogueDBAdapter.KEY_ROWID, mRowId);
		} else {
			//there is nothing todo
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
	}
	
	/*
	@Override
	protected void onResume() {
		super.onResume();
		fillAnthology();
	}
	*/

	private void saveState() {
		int anthology = CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS;
		if (mSame.isChecked()) {
			anthology = CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR;
		}
		float rating = book.getFloat(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING));
		boolean read = book.getInt(book.getColumnIndex(CatalogueDBAdapter.KEY_READ))==0? false:true;
		String notes = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES));
		String isbn = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN));
		String publisher = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER));
		String date_published = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED));
		String bookshelf = null;
		String series = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES));
		String series_num = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NUM));
		String list_price = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE));
		String location = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION));
		String read_start = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START));
		String read_end = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END));
		boolean audiobook = (book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUDIOBOOK))==0? false:true);
		boolean signed = (book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED))==0? false:true);
		int pages = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES));

		if (mRowId == null || mRowId == 0) {
			//This should never happen
			//long id = mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num);
			Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show();
			finish();
		} else {
			mDbHelper.updateBook(mRowId, bookAuthor, bookTitle, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, audiobook, signed);
		}
		return;
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	}

}

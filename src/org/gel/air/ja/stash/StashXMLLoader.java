package org.gel.air.ja.stash;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Stack;
import java.util.zip.DeflaterOutputStream;

import javax.swing.Timer;

import org.apache.xerces.parsers.SAXParser;
import org.gel.air.ja.msg.AbstractMessageManager;
import org.gel.air.ja.msg.Message;
import org.gel.air.ja.stash.events.StashEvents;
import org.gel.air.ja.stash.events.StashUpdateEvents;
import org.gel.air.util.IOUtils;
import org.gel.air.util.SystemUtils;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Not complete documentation.  Reads and writes complete Stashes as XML.
 * Methods for writing and loading all to/from a defaults Stash obey the
 * following rules:  If a tag is top-level in its file, then it is put
 * in a defaults Stash for objects of its type, if one exists.  In this case, if it is
 * referenced by another Stash object (meaning it has to be declared as 
 * tag_name=obj_id), it will be placed in the Stash as a String value.
 * Only objects declared implicitly within another object are placed
 * in their parent object as the actual Stash, and in this case, will
 * not be put in any defaults object.  Objects with an id equal to their
 * class type are created as blueprints for the object type.  When one is
 * read, a default Stash with that type is created with the read object as
 * its only member with the key also equal to the object type.
 * 
 * Lists: Stash structure compared to XML structure
 * Lists of all types are declared similarly in XML:
 * <List id=list_name list_class=class_type_stored/>.  In Stash structure,
 * if the class_type_stored is a Stash, it will be stored in the list with
 * each items id as a key, and the Stash as the item (if the Stash is a top-level object,
 * it will be stored as id, technically).  Otherwise, the key can be defined to be 
 * anything unique to the list.  Any key can be changed in memory by changing
 * it in the appropriate Stash, and will still be written correctly.  Lists are 
 * stored to XML files as follows:
 * If the item is a top-level Stash or a String (non-Stash) value, the list will
 * appear <List id=list_name item1_id=item1_val item2_id=item2_val list_class=class_type etc/>
 * Where itemx_id is the key the item is under in the stash, and itemx_val is
 * the Stash id if it's a top level stash, otherwise, itemx_val is the String value.
 * If the item is a non top-level Stash, the list will appear <List list_class=class_type
 * id=list_name/><class_type_stored key1=val1a key2=val2a etc/><class_type_stored
 * key1=val1b key2=val2b etc/></List> where keyx is a key in the Stash, and valx is the
 * value in the Stash.
 * 
 * Tag order (key order) doesn't matter, and these item types and list types can be chained
 * in any way.
 *
 * @author Anna I Rissman/James Lowden
 *
 */
public class StashXMLLoader extends DefaultHandler implements StashConstants, 
		ActionListener {

	public static int DELAY = 30000000;
	protected Stack hash_stack;
	protected static Stash defaults;
	protected SAXParser parser;
	protected StringBuffer char_buffer;
	protected String root;
	protected AbstractMessageManager events;
	protected StashEvents data_handler;
	protected HashSet <Stash> changed;
	protected Timer timer;

	static {
		defaults = new Stash ();
		Stash.defaults = defaults;
	}
	public StashXMLLoader (String root_dir, AbstractMessageManager eve) {
		events = eve;
		data_handler = new StashEvents (events);
		root = new File (root_dir).getAbsolutePath();
		hash_stack = new Stack ();
		changed = new HashSet ();
		timer = new Timer (DELAY, this);
 		// construct parser; set features
		parser = new SAXParser();
		try {
			parser.setFeature ("http://xml.org/sax/features/namespaces", true);
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
   		// set handlers
		parser.setContentHandler (this);
		char_buffer = new StringBuffer ();
	}
	
	public void stashChanged (Stash current) {
		synchronized (changed) {
			changed.add (current);
		}
	}
	
	public void writeIfChanged (Stash stash) {
		if (stash != null && changed.contains (stash))
			writeFile (stash);
	}
	
	public File getAssociatedFile (String id, String suffix) {
		int ind = id.indexOf (KEY_SEPARATOR);
		if (suffix.length () > 0 && !suffix.startsWith ("."))
			suffix = "." + suffix;
		if (!id.endsWith (suffix))
			id += suffix;
		File file = new File (new File (root, id.substring(0, ind)), 
				id.substring(ind + 1));
		return file;
	}

	public File getFileForStash (String id) {
		return getAssociatedFile (id, ".xml");
	}

	public void startElement(String uri, String localpart, String rawname, Attributes attributes) {
		try {
//			localpart = localpart.substring (localpart.indexOf (':'), localpart.length ());
			Stash attrs = new Stash ();
			attrs.put (CLASS_TYPE_STRING, localpart);
			if (attributes != null) {
	            	int length = attributes.getLength();
				for (int i = 0; i < length; i++) {
                			attrs.put (attributes.getLocalName(i), dequote (attributes.getValue(i)));
				}
			}
			String id = attrs.getString (ID);
			if (id != null) {
				Stash temp = defaults.getHashtable (localpart);
				if (temp != null) {
					temp = temp.getHashtable (id);
					if (temp != null) {
						temp.putAll (attrs);
						attrs = temp;
					}
				}
			}
			if (localpart.equals (attrs.get (ID))) {
				defaults.put (localpart, attrs);
			}
			else {
				//id made by makeKey is used only internally for store and doesn't get
				//written.
				if (attrs.get (ID) == null)
					attrs.put (ID, localpart);
				id = makeKey ((String) attrs.get (ID));
				((Stash) hash_stack.peek ()).put (id, attrs);
			}
			hash_stack.push (attrs);
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
	}


	@Override
	public void characters (char [] chars, int start, int length) {
		char_buffer.append (chars, start, length);
	}


	@Override
	public void endElement(String uri, String localpart, String rawname) {
		try {
			Stash temp = (Stash) hash_stack.pop ();
			String text = char_buffer.toString ().trim ();
			//not sure if this if statement does anything or should
			if (text.length () > 0) {
				Stash parent = (Stash) hash_stack.peek ();
				parent.put (temp.get (ID), text);
				char_buffer.setLength (0);
			}
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
	}



	public String dequote (String start) {
		if (start.startsWith ("\""))
			start = start.substring (1, start.length () - 1);
		return start;
	}
	
	public void writeXMLFile (Stash data, File file) {
		writeXMLFile (data, file.getAbsolutePath());
	}


	public synchronized void writeXMLFile (Stash data, String file_name) {
		try {
			PrintStream out = new PrintStream (new BufferedOutputStream (
					new FileOutputStream (file_name)));
			writeXMLFile (data, out);
			out.close ();
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
	}
		
	public synchronized void writeCompressedFile (Stash data, String file_name) {
		try {
			PrintStream out = new PrintStream (new DeflaterOutputStream (
					new BufferedOutputStream (new FileOutputStream (file_name))));
			writeXMLFile (data, out);
			out.close ();
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
	}
	
	public synchronized void writeXMLFile (Stash data, PrintStream out) {
		try {
			out.println (XML_HEAD);
			writeXML (data, out, true);
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
	}

	protected synchronized void writeXML (Stash data, PrintStream out, boolean close) {
		try {
			String tag = (String) data.get (CLASS_TYPE_STRING);
			out.print ("<" + tag);
			LinkedList subs = new LinkedList ();
			Iterator itty = null;
			itty = data.keySet ().iterator ();
			String key = null;
			Object val = null;
			while (itty.hasNext ()) {
				key = (String) itty.next ();
				val = data.get (key);
				if (val instanceof Stash)
					subs.add (val);
				else if (!key.equals (CLASS_TYPE_STRING))
					out.print (" " + key + " = \"" + val + "\"");
			}
			out.println (">");
			itty = subs.iterator ();
			while (itty.hasNext ()) {
				writeXML ((Stash) itty.next (), out, true);
			}
			if (close)
				out.println ("</" + tag + ">");
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
	}

	//TODO the stash passed in was a new empty one, think we need parent, so
	//changed call
	public synchronized void readStringInto (Stash hash, String str) {
		readInto (hash, new StringReader (str));
	}

	public synchronized void readInto (Stash hash, Reader in) {
		try {
			if (hash == null)
				hash = defaults;
			hash_stack.push (hash);
			parser.parse(new InputSource (in));
			hash_stack.pop ();
			in.close ();
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
	}
	
	public synchronized void readInto (Stash hash, InputStream in) {
		try {
			if (hash == null)
				hash = defaults;
			hash_stack.push (hash);
			parser.parse(new InputSource (in));
			hash_stack.pop ();
			in.close ();
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
	}

	public synchronized void readInto (Stash hash, String in) {
		try {
			if (hash == null)
				hash = defaults;
			hash_stack.push (hash);
			parser.parse(in);
			hash_stack.pop ();
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
	}


	public Stash createNew (String type) {
		Stash noo = defaults.getHashtable (type).replicate ();
		writeXMLFile (noo, new File (root, noo.getString (ID)));
		defaults.getHashtable (type).put (makeKey (noo.getString (ID)), noo);
		return noo;
	}
	
	/**
	 * use this method to get a Stash that may or may not be loaded.
	 * @param id
	 * @return
	 */
	public Stash getStash (String obj_id) {
		System.out.println ("obj: " + obj_id);
		String ct = obj_id.substring(0, obj_id.indexOf(KEY_SEPARATOR));
		String id = makeKey (obj_id);
		Stash stash = defaults.getHashtable(ct).getHashtable(id);
		if (stash == null) {
			File file = getFileForStash (obj_id);
			if (!file.exists ()) {
				stash = getRemoteStash (obj_id);
				stashChanged (stash);
			}
			else {
				loadFile (defaults.getHashtable(ct), file);
				stash = defaults.getHashtable(ct).getHashtable(id);
			}
		}	
		return stash;
	}
	
	public Stash getRemoteStash (String id) {
		String ret = SystemUtils.makeUniqueString ();
		Message msg = new Message (ret, GET_NS + id);
		msg = (Message) events.getReply (msg, ret);
		String data = msg.getMessage ();
		if (data.length () == 0)
			return null;
		else {
			readStringInto (defaults.getHashtable(id.substring(0, 
					id.indexOf(KEY_SEPARATOR))), data);
			return getStash (id);
		}
	}	

	public static Stash getDefaults () {
		return defaults;
	}

	public static String makeKey (String id) {
		int ind = id.indexOf (KEY_SEPARATOR);
		if (ind > -1)
			id = ITEM + id.substring (ind + 1, id.length ());
		return id;
	}

	/**
	 * Defaults file contains object types and default keys and values.
	 * A key assigned to an empty value means the key should be present,
	 * but has no default value assigned to it.  A key assigned to [] means
	 * the field is optional (this means a key cannot be assigned a default
	 * value of [], or, if it is, the key will be assumed to be optional).
	 * Optional keys aren't created when a new Stash is created from a
	 * default used as a blueprint.
	 *   
	 * @param file_base
	 * @param defaults
	 */
	public void loadAll (String file_base, String defaults) {
		try {
			long millis = System.currentTimeMillis();
			parser.parse (new File (file_base, defaults).getAbsolutePath ());
			loadAll (new File (file_base));
			System.out.println ("time: " + (System.currentTimeMillis() - millis));
		}
		catch (Exception e) {
			e.printStackTrace ();
		}
	}
	
	public void loadDefaults (File defaults) throws Exception {
		parser.parse (defaults.getAbsolutePath ());
	}


	public void loadAll (File path) {
		File root_dir = new File (root);
		Stash clazz = defaults.getHashtable (path.getName ());
		File [] f = path.listFiles ();
		for (int i = 0; i < f.length; i++) {
			try {
				if (f [i].isDirectory ())
					loadAll (f [i]);
				else				
					loadFile (clazz, f [i]);
			}
			catch (Exception e) {
				e.printStackTrace ();
			}
		}
	}
	
	/**
	 * file 
	 * @param file
	 */
	public void loadFile (Stash clazz, File file) {
		try {
			String path = file.getAbsolutePath();
			if (!path.startsWith(root) || !path.endsWith(".xml") ||
					!(path.indexOf(clazz.getString(ID)) > -1))
				return;
			BufferedInputStream buf = new BufferedInputStream (
					new FileInputStream (file.getAbsolutePath ()),
					IOUtils.BUFFER_SIZE);
			readInto (clazz, buf);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	protected void writeFile (Stash stash) {
		try {
			synchronized (stash) {
				File file = getFileForStash (stash.getString (ID));
				String file_name = file.getAbsolutePath ();
				PrintStream out = new PrintStream (new BufferedOutputStream (
						new FileOutputStream (file_name)));
				writeXMLFile (stash, out);
				out.close ();
				synchronized (changed) {
					changed.remove (stash);
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace ();
		}
	}
	
	public void actionPerformed (ActionEvent e) {
		Stash [] items;
		synchronized (changed) {
			items = new Stash [changed.size ()];
			changed.toArray (items);
		}
		for (int i = 0; i < items.length; i++) {
			synchronized (items [i]) {
				writeFile (items [i]);
			}
		}
	}

	/**
	 * Make a list into an open vector.  
	 * @param source
	 * @param clazz		True if the list is a list of instances of a top-level
	 * 					class being loaded from defaults.
	 * @return
	 */
	public StashList populateVector (Stash source, boolean clazz) {
		StashList vector = new StashList ();
		while (vector.size () > 0)
			vector.removeElement (vector.lastElement ());
		String class_type = null;
		if (clazz)
			class_type = source.getString (ID);
		else
			class_type = source.getString (LIST_CLASS_FIELD);
		for (Iterator keys = source.keySet ().iterator (); keys.hasNext ();) {
			String key = (String) keys.next ();
			Stash value = source.getHashtable (key);
			if (value == null) {
				if (key.startsWith (ITEM)) {
					key = key.substring(ITEM.length());
					key = class_type + KEY_SEPARATOR + key;
					System.out.println (key);
					value = getStash (key);
				}
			}
			if (value != null && ((Stash) value).getString (ID).startsWith
					(class_type + KEY_SEPARATOR))
				vector.addElement (value);
		}
		//Collections.sort (vector);
		if (data_handler != null)
			data_handler.addCallbackTo (source, (StashList) vector);
		return vector;
	}

	public Stash makeList (String list_class, String id) {
		Stash list = new Stash (LIST_CLASS, id);
		list.put (LIST_CLASS_FIELD, list_class);
		return list;
	}

	public StashEvents getNoCastHashEvents () {
		return data_handler;
	}

}
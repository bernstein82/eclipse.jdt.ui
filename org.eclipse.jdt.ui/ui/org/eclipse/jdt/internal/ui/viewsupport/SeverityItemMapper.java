package org.eclipse.jdt.internal.ui.viewsupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Item;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;

import org.eclipse.jface.viewers.ILabelProvider;
public class SeverityItemMapper {
	// map from path to item
	private HashMap fPathToItem;

	public SeverityItemMapper() {
		fPathToItem= new HashMap();
	}

	/**
	 * Updates the icons of all mapped elements containing to the changed elements.
	 * Must be called from the UI thread.
	 */
	public void severitiesChanged(Collection changedElements, ILabelProvider lprovider) {
		Iterator elements= changedElements.iterator();
		while (elements.hasNext()) {
			IPath curr= (IPath) elements.next();
			Object obj= fPathToItem.get(curr);
			if (obj == null) {
				// not mapped
			} else if (obj instanceof Item) {
				refreshIcon(lprovider, (Item)obj);
			} else { // List of Items
				List list= (List) obj;
				for (int i= 0; i < list.size(); i++) {
					refreshIcon(lprovider, (Item) list.get(i));
				}
			}
		}
	}
	
	private void refreshIcon(ILabelProvider lprovider, Item item) {
		if (!item.isDisposed()) { // defensive code
			Object data= item.getData();
			Image image= lprovider.getImage(data);
			if (image != null) {
				item.setImage(image);
			}
		}
	}

	/**
	 * Adds a new item to the map.
	 * @param resource The coresponding resource if the data attached to the item.
	 * @param item The item (with an attached data element)
	 */
	public void addToMap(IResource resource, Item item) {
		IPath path= resource.getFullPath();
		Object existingMapping= fPathToItem.get(path);
		if (existingMapping == null) {
			fPathToItem.put(path, item);
		} else if (existingMapping instanceof Item) {
			ArrayList list= new ArrayList(2);
			list.add(existingMapping);
			list.add(item);
			fPathToItem.put(path, list);
		} else { // List			
			List list= (List)existingMapping;
			list.add(item);
		}		
	}

	/**
	 * Removes an element from the map.
	 * The item corresponding to the element is removed from the map.
	 */	
	public void removeFromMap(IResource resource, Object element) {
		Object existingMapping= fPathToItem.get(resource.getFullPath());
		if (existingMapping == null) {
			return;
		} else if (existingMapping instanceof Item) {
			fPathToItem.remove(resource.getFullPath());
		} else { // List
			List list= (List) existingMapping;
			for (int i= 0; i < list.size(); i++) {
				Item item= (Item) list.get(i);
				if (!item.isDisposed()) {
					Object data= item.getData();
					if (data == null || data.equals(element)) {
						list.remove(item);
						break;
					}
				}
			}			
		}
	}
	
	/**
	 * Clears the map.
	 */
	public void clearMap() {
		fPathToItem.clear();
	}



}

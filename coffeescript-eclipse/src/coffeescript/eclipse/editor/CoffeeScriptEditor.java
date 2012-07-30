package coffeescript.eclipse.editor;

import org.eclipse.ui.editors.text.TextEditor;

/**
 * @author Denis Stepanov
 */
public class CoffeeScriptEditor extends TextEditor {

	public CoffeeScriptEditor() {
		super();
		setSourceViewerConfiguration(new CoffeeScriptConfiguration());
	}
}

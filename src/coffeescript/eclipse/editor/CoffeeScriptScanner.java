package coffeescript.eclipse.editor;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.jface.text.rules.*;
import org.eclipse.jface.text.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

import coffeescript.lang.CoffeeScriptLexer;
import coffeescript.lang.CoffeeScriptLexerInput;
import coffeescript.lang.CoffeeScriptLexerStringInput;
import coffeescript.lang.CoffeeScriptRegexpLexer;
import coffeescript.lang.CoffeeScriptRegexpTokenId;
import coffeescript.lang.CoffeeScriptStringLexer;
import coffeescript.lang.CoffeeScriptStringTokenId;
import coffeescript.lang.CoffeeScriptTokenId;
import coffeescript.lang.CoffeeScriptTokenId.Category;

/**
 * @author Denis Stepanov
 */
public class CoffeeScriptScanner implements ITokenScanner {

	private CoffeeScriptLexerStringInput lexerInput;
	private Deque<EmbbeddedLexer> lexers;
	private Map<CoffeeScriptTokenId.Category, Token> tokens = new HashMap<CoffeeScriptTokenId.Category, Token>(CoffeeScriptTokenId.values().length);

	@Override
	public int getTokenLength() {
		return lexerInput.readLength();
	}

	@Override
	public int getTokenOffset() {
		return lexerInput.getOffset();
	}

	@Override
	public IToken nextToken() {
		while (!lexers.isEmpty()) {
			EmbbeddedLexer lexer = lexers.peek();
			IToken token = lexer.nextToken();
			if (lexer.isEmbedded()) {
				EmbbeddedLexer embbeddedLexer = null;
				switch (lexer.getEmbeddedType()) {
				case CS:
					embbeddedLexer = new CSLexer(lexerInput.embedded());
					break;
				case HEREGEX:
					embbeddedLexer = new RegexpLexer(lexerInput.embedded());
					break;
				case STRING:
					embbeddedLexer = new StringLexer(lexerInput.embedded());
					break;
				}
				lexers.push(embbeddedLexer);
				return nextToken();
			} else if (token.isEOF()) {
				lexers.pop();
			} else {
				return token;
			}
		}
		return Token.EOF;
	}

	private IToken asIToken(CoffeeScriptTokenId coffeeScriptTokenId) {
		Token token = tokens.get(coffeeScriptTokenId.getCategory());
		if (token == null) {
			RGB rgb = new RGB(0, 0, 0);
			Color background = null;
			int style = SWT.NORMAL;
			switch (coffeeScriptTokenId.getCategory()) {
			case COMMENT_CAT:
				rgb = CoffeeScriptColorConstants.LINE_COMMENT;
				break;
			case KEYWORD_CAT:
				rgb = CoffeeScriptColorConstants.KEYWORD;
				style |= SWT.BOLD;
				break;
			case STRING_CAT:
				rgb = CoffeeScriptColorConstants.STRING;
				break;
			case REGEXP_CAT:
				rgb = CoffeeScriptColorConstants.KEYWORD;
				break;
			case FIELD_CAT:
				rgb = CoffeeScriptColorConstants.FIELD;
				break;
			case WHITESPACE_CAT:
				return Token.WHITESPACE;
			case ERROR_CAT:
				// TODO errors highlighting
				break;
			}
			Color color = new Color(Display.getCurrent(), rgb);
			token = new Token(new TextAttribute(color, background, style));
			tokens.put(coffeeScriptTokenId.getCategory(), token);
		}
		return token;
	}

	@Override
	public void setRange(IDocument document, int offset, int length) {
		lexers = new LinkedList<EmbbeddedLexer>();
		try {
			lexerInput = new CoffeeScriptLexerStringInput(document.get(0, document.getLength()));
			lexers.push(new CSLexer(lexerInput));
			if (offset > 0) {
				while (getTokenOffset() + getTokenLength() < offset) {
					nextToken(); // skip tokens to offset
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private class RegexpLexer extends EmbbeddedLexer {

		private final CoffeeScriptRegexpLexer lexer;

		public RegexpLexer(CoffeeScriptLexerInput lexerInput) {
			lexer = new CoffeeScriptRegexpLexer(lexerInput);
		}

		@Override
		public IToken nextToken() {
			embedded = false;
			CoffeeScriptRegexpTokenId coffeeScriptRegexpTokenId = lexer.nextToken();
			if (coffeeScriptRegexpTokenId == null) {
				return Token.EOF;
			} else if (coffeeScriptRegexpTokenId == CoffeeScriptRegexpTokenId.EMBEDDED) {
				embedded = true;
				embeddedType = Type.CS;
				return null;
			} else if (coffeeScriptRegexpTokenId == CoffeeScriptRegexpTokenId.COMMENT) {
				return asIToken(CoffeeScriptTokenId.COMMENT);
			}
			return asIToken(CoffeeScriptTokenId.STRING);
		}

	}

	private class StringLexer extends EmbbeddedLexer {

		private final CoffeeScriptStringLexer lexer;

		public StringLexer(CoffeeScriptLexerInput lexerInput) {
			lexer = new CoffeeScriptStringLexer(lexerInput);
		}

		@Override
		public IToken nextToken() {
			embedded = false;
			CoffeeScriptStringTokenId coffeeScriptStringTokenId = lexer.nextToken();
			if (coffeeScriptStringTokenId == null) {
				return Token.EOF;
			} else if (coffeeScriptStringTokenId == CoffeeScriptStringTokenId.EMBEDDED) {
				embedded = true;
				embeddedType = Type.CS;
				return null;
			}
			return asIToken(CoffeeScriptTokenId.STRING);
		}

	}

	private class CSLexer extends EmbbeddedLexer {

		private CoffeeScriptLexer lexer;

		public CSLexer(CoffeeScriptLexerInput lexerInput) {
			this.lexer = new CoffeeScriptLexer(lexerInput);
		}

		@Override
		public IToken nextToken() {
			CoffeeScriptTokenId coffeeScriptTokenId = lexer.nextToken();
			if (coffeeScriptTokenId == CoffeeScriptTokenId.STRING) {
				embedded = true;
				embeddedType = Type.STRING;
				return null;
			} else if (coffeeScriptTokenId == CoffeeScriptTokenId.HEREGEX) {
				embedded = true;
				embeddedType = Type.HEREGEX;
				return null;
			} else {
				embedded = false;
				if (coffeeScriptTokenId == null) {
					return Token.EOF;
				} else if (coffeeScriptTokenId.getCategory() == Category.WHITESPACE_CAT) {
					return Token.WHITESPACE;
				}
				return asIToken(coffeeScriptTokenId);
			}
		}

	}

	enum Type {
		CS, STRING, HEREGEX
	}

	private static abstract class EmbbeddedLexer {

		protected boolean embedded;
		protected Type embeddedType;

		public abstract IToken nextToken();

		public boolean isEmbedded() {
			return embedded;
		}

		public Type getEmbeddedType() {
			return embeddedType;
		}

	}

}

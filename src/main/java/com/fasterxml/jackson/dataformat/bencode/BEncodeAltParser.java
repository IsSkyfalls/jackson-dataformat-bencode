package com.fasterxml.jackson.dataformat.bencode;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Stack;

import static com.fasterxml.jackson.core.JsonToken.*;

public class BEncodeAltParser extends ParserBase {

    private static byte[] MAX_LONG_STR = "9223372036854775807".getBytes(Charset.forName("ISO-8859-1"));

    private static byte[] MAX_INT_STR = "2147483647".getBytes(Charset.forName("ISO-8859-1"));

    private final InputStream in;

    private ObjectCodec codec;

    private int strLen;

    private final byte[] byteBuf;

    private final char[] charBuf;

    private final Stack<JsonToken> stack = new Stack<>();

    protected BEncodeAltParser(InputStream in, IOContext ctx){
        super(ctx, 0);
        this.in = in;
        // one for sign(+/-), and one for end_marker(e)
        // though this buffer is usually much larger
        byteBuf = ctx.allocReadIOBuffer(MAX_LONG_STR.length + 2);
        charBuf = ctx.allocConcatBuffer();
    }

    @Override
    protected void _closeInput() throws IOException{
        in.close();
    }

    @Override
    public ObjectCodec getCodec(){
        return codec;
    }

    @Override
    public void setCodec(ObjectCodec codec){
        this.codec = codec;
    }

    @Override
    public JsonToken nextToken() throws IOException{
        in.mark(1);
        switch (in.read()) {
            case 'd':
                stack.add(START_OBJECT);
                return _currToken = START_OBJECT;
            case 'l':
                stack.add(START_ARRAY);
                return _currToken = START_ARRAY;
            case 'i':
                // getIntValue is immediately called after return, we need to parse first
                _parseNumericValue(NR_UNKNOWN);
                return _currToken = VALUE_NUMBER_INT;
            case 'e':
                _currToken = stack.pop();
                // this wont actually happen, since nextToken is not called when the whole object is done, after the last END_OBJECT;
                if(_currToken == null){
                    _reportError("ran out of nested layers");
                }
                switch (_currToken) {
                    case START_OBJECT:
                        return _currToken = END_OBJECT;
                    case START_ARRAY:
                        return _currToken = END_ARRAY;
                    default:
                        throw new RuntimeException("invalid prev state"); // sanity check
                }
            case -1:
                if(stack.size() != 0){
                    _reportError("unexpected EOF");
                }
            default:
                in.reset();
                if(_currToken == FIELD_NAME){
                    return _currToken = VALUE_STRING;
                }
                if(_currToken == START_ARRAY){
                    return VALUE_STRING; // this is not kept in _currToken because the next value will still be string if 'e' is not encountered
                }
                return _currToken = FIELD_NAME;
        }
    }

    @Override
    public JsonParser skipChildren() throws IOException{
        if(_currToken == VALUE_STRING){
            getBinaryValue(null);
        } else if(_currToken == VALUE_NUMBER_INT){
            // _parseNumericValue has already been called in nextToken
            // the integer was consumed there
        } else if(_currToken == START_ARRAY || _currToken == START_OBJECT){
            int layer = 1;
            do {
                in.mark(1);
                int c = in.read();
                switch (c) {
                    case 'i':
                    case 'l':
                    case 'd':
                        layer++;
                        break;
                    case 'e':
                        layer--;
                        break;
                    case -1:
                        throw new IOException("unexpected EOF");
                    default:
                        // handle text containing e
                        if(c >= '0' && c <= '9'){
                            in.reset();
                            getBinaryValue(null);
                        }
                }
            } while (layer > 0);
        }
        return this;
    }

    @Override
    public String getCurrentName() throws IOException{
        return getText();
    }

    @Override
    public String getText() throws IOException{
        return new String(getBinaryValue());
    }

    @Override
    public char[] getTextCharacters() throws IOException{
        return getText().toCharArray();
    }

    @Override
    public byte[] getBinaryValue(Base64Variant variant) throws IOException{
        _parseNumericValue(NR_INT); // this does the real parsing
        strLen = getIntValue(); // getIntValue simply returns the previously read value
        try (ByteArrayBuilder builder = new ByteArrayBuilder()) {
            int len = 0;
            while (strLen > 0 && (len = in.read(byteBuf, 0, Math.min(strLen, byteBuf.length))) != -1) {
                builder.write(byteBuf, 0, len);
                strLen -= len;
            }
            return builder.toByteArray();
        }
    }

    @Override
    public int getTextLength() throws IOException{
        return strLen;
    }

    @Override
    public int getTextOffset() throws IOException{
        return 0;
    }

    @Override
    protected void _parseNumericValue(int expType) throws IOException{
        JsonToken tmp = _currToken;
        _currToken = VALUE_NUMBER_INT;
        _textBuffer.resetWithShared(charBuf, 0, 0);
        int c;
        while ((c = in.read()) != ':' && c != 'e' && c != -1) {
            _textBuffer.append((char) c);
        }
        _intLength = _textBuffer.size();
        super._parseNumericValue(expType);
        _currToken = tmp;
    }
}

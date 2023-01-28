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

    private final Stack<JsonToken> stack = new Stack<>();
//
//    protected BEncodeAltParser(Reader r, ObjectCodec codec, IOContext ctx, int features){
//        super(ctx, features);
//        this.in = r;
//        this.codec = codec;
//        // one for sign(+/-), and one for end_marker(e)
//        tokenBuf = ctx.allocTokenBuffer(MAX_LONG_STR.length + 2);
//        byteBuf = ctx.allocReadIOBuffer();
//    }

    protected BEncodeAltParser(InputStream in, IOContext ctx){
        super(ctx, 0);
        this.in = in;
        // one for sign(+/-), and one for end_marker(e)
        // though this buffer is usually much larger
        byteBuf = ctx.allocReadIOBuffer(MAX_LONG_STR.length + 2);
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
                if(_currToken == null){
                    _reportError("ran out of nested layers");
                }
                switch (_currToken) {
                    case START_OBJECT:
                        return _currToken = JsonToken.END_OBJECT;
                    case START_ARRAY:
                        return _currToken = JsonToken.END_ARRAY;
                    default:
                        throw new RuntimeException("invalid prev state");
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
//                if(_currToken==VALUE_STRING){
//                    // we might be in an array, continue
//                    return NOT_AVAILABLE;
//                }
                return _currToken = FIELD_NAME;
//                if(_currToken != FIELD_NAME){
//                    strLen = getIntValue();
//                    return _currToken = FIELD_NAME;
//                } else {
//
//                }
        }
    }

    @Override
    public JsonParser skipChildren() throws IOException{
        if(_currToken == VALUE_STRING){
            in.skip(strLen);
        } else if(_currToken == VALUE_NUMBER_INT){
            // _parseNumericValue has already been called in nextToken
            // the integer was consumed there
        } else if(_currToken == START_ARRAY || _currToken == START_OBJECT){
            int layer = 1;
            do {
                switch (in.read()) {
                    case 'i':
                    case 'l':
                    case 'd':
                        layer++;
                        break;
                    case 'e':
                        layer--;
                        break;
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
        _textBuffer.setCurrentLength(0);
        int c;
        while ((c = in.read()) != ':' && c != 'e' && c != -1) {
            _textBuffer.append((char) c);
        }
        _intLength=_textBuffer.size();
        super._parseNumericValue(expType);
        _currToken = tmp;
    }

    //    @Override
//    public int getIntValue() throws IOException{
//        _parseNumericValue(0);
//        return super.getIntValue();
//    }
//
//    @Override
//    public long getLongValue() throws IOException{
//        return super.getLongValue();
//    }

//    @Override
//    protected void _parseNumericValue(int expType) throws IOException{
//        r.mark(MAX_LONG_STR.length + 2);
//        int len = r.read(tokenBuf, 0, tokenBuf.length);
//        if(len == -1){
//            _reportError("EOF reached unexpectedly");
//        }
//        for (int i = 0; i < len; i++) {
//            if(tokenBuf[i] == 'e' || tokenBuf[i] == ':'){
//                if(i == 0){
//                    _reportError("Empty integer value");
//                }
//                len = i;
//                break;
//            }
//        }
//        int offset = 0;
//        if(tokenBuf[0] == '-'){
//            _numberNegative = true;
//            offset = 1;
//        }
//        if(len >= MAX_LONG_STR.length){
//            _reportError("Integer overflow. Reading BigInteger is currently not supported");
//        }
//        if(_numberNegative){
//            // parseLong requires len to be between [10,18]
//            if(len >= 10){
//                _numberLong = -NumberInput.parseLong(tokenBuf, offset, len);
//                if(_numberLong > MIN_INT_L){
//                    _numberInt = (int) _numberLong;
//                    _numTypesValid = NR_INT;
//                } else {
//                    _numTypesValid = NR_LONG;
//                }
//            } else {
//                _numberInt = -NumberInput.parseInt(tokenBuf, offset, len);
//                _numTypesValid = NR_INT;
//            }
//        } else {
//            if(len >= 10){
//                _numberLong = NumberInput.parseLong(tokenBuf, offset, len);
//                if(_numberLong < MAX_INT_L){
//                    _numberInt = (int) _numberLong;
//                    _numTypesValid = NR_INT;
//                } else {
//                    _numTypesValid = NR_LONG;
//                }
//            } else {
//                _numberInt = NumberInput.parseInt(tokenBuf, offset, len);
//                _numTypesValid = NR_INT;
//            }
//        }
//        r.reset();
//        r.skip(len + 1);
//    }
}

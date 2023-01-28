package com.fasterxml.jackson.dataformat.bencode;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.bencode.types.Animal;
import com.fasterxml.jackson.dataformat.bencode.types.Torrent;
import com.fasterxml.jackson.dataformat.bencode.types.User;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import sun.nio.cs.StandardCharsets;

import java.io.*;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class TestFullDataBindingRead {
    private ObjectMapper underTest;

    @Before
    public void startUp() throws IOException{
        underTest = new BEncodeMapper();
    }

    @Test
    public void testReadValueFromStream() throws Exception{
        InputStream in = new ByteArrayInputStream(TestUtils.TUTORIAL_EXAMPLE_ENCODED.getBytes("ISO-8859-1"));
        User u = underTest.readValue(in, User.class);
        assertThat(u.getGender(), is(User.Gender.MALE));
        assertThat(u.isVerified(), is(false));
        assertThat(u.getName().getFirst(), is("Joe"));
        assertThat(u.getName().getLast(), is("Sixpack"));
        assertThat(u.getUserImage(), is(TestUtils.BINARY_DATA));
    }

    @Test
    public void testReadComplexValue() throws Exception{
        Torrent ubuntu = underTest.readValue(
                new File("src/test/resources/ubuntu-13.10-desktop-amd64.iso.torrent"), Torrent.class);
        assertThat(ubuntu.getAnnounce(), is("http://torrent.ubuntu.com:6969/announce"));
        assertThat(ubuntu.getAnnounceList(), notNullValue());
        assertThat(ubuntu.getAnnounceList().size(), is(2));
        assertThat(ubuntu.getAnnounceList().get(0), hasItems("http://torrent.ubuntu.com:6969/announce"));
        assertThat(ubuntu.getAnnounceList().get(1), hasItems("http://ipv6.torrent.ubuntu.com:6969/announce"));
        assertThat(ubuntu.getComment(), is("Ubuntu CD releases.ubuntu.com"));
        assertThat(ubuntu.getCreationDate(), is(1382003607));
        assertThat(ubuntu.getInfo(), notNullValue());
        assertThat(ubuntu.getInfo().getLength(), is(925892608L));
        assertThat(ubuntu.getInfo().getPieceLength(), is(524288));
        assertThat(ubuntu.getInfo().getPieces().length, is(35320));
    }

    @Test
    public void testReadUnknownField() throws IOException{
        ObjectMapper mapper=new BEncodeMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        Assert.assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        Animal o = mapper.readValue("d3:agei80e4:type5:humane".getBytes(), Animal.class);
        Assert.assertEquals("human",o.type);
    }
}

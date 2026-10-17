package com.saasno.utility.wrapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

public class MultipleReadResponseWrapper extends HttpServletResponseWrapper {

    private ByteArrayOutputStream output = new ByteArrayOutputStream();
    private ServletOutputStream servletOutStream = new ServletOutputStream() {
        @Override
        public void write(int b) {
            output.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    };

    private PrintWriter writer = new PrintWriter(output, true);

    public MultipleReadResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return servletOutStream;
    }

    @Override
    public PrintWriter getWriter() {
        return writer;
    }

    public String getBody() {
        return output.toString();
    }
}

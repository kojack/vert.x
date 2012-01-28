package org.vertx.java.newtests;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonMessage;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestUtils {

  private static final Logger log = Logger.getLogger(TestUtils.class);

  private final Thread th;
  private final Long contextID;
  private Map<String, Handler<Message<JsonObject>>> handlers = new HashMap<>();

  public TestUtils() {
    this.th = Thread.currentThread();
    this.contextID = Vertx.instance.getContextID();
  }

  public void azzert(boolean result) {
    azzert(result, null);
  }

  public void azzert(boolean result, String message) {
    JsonObject jsonObject = new JsonObject().putString(EventFields.TYPE_FIELD, EventFields.ASSERT_EVENT)
       .putString(EventFields.ASSERT_RESULT_FIELD, result ? EventFields.ASSERT_RESULT_VALUE_PASS : EventFields.ASSERT_RESULT_VALUE_FAIL);
    if (message != null) {
      jsonObject.putString(EventFields.ASSERT_MESSAGE_FIELD, message);
    }
    if (!result) {
      jsonObject.putString(EventFields.ASSERT_STACKTRACE_FIELD, getStackTrace(new Exception()));
    }
    sendMessage(jsonObject);
    if (!result) {
      throw new AssertionError(message);
    }
  }

  public void appReady() {
    sendEvent(EventFields.APP_READY_EVENT);
  }

  public void appStopped() {
    sendEvent(EventFields.APP_STOPPED_EVENT);
  }

  public void testComplete() {
   JsonObject jsonObject = new JsonObject().putString(EventFields.TYPE_FIELD, EventFields.TEST_COMPLETE_EVENT)
     .putString(EventFields.TEST_COMPLETE_NAME_FIELD, "unused");
    sendMessage(jsonObject);
  }

  public void startTest(String testName) {
    JsonObject jsonObject = new JsonObject().putString(EventFields.TYPE_FIELD, EventFields.START_TEST_EVENT)
      .putString(EventFields.START_TEST_NAME_FIELD, testName);
    sendMessage(jsonObject);
  }

  public void exception(Throwable t, String message) {
    JsonObject jsonObject = new JsonObject().putString(EventFields.TYPE_FIELD, EventFields.EXCEPTION_EVENT)
      .putString(EventFields.EXCEPTION_MESSAGE_FIELD, message).putString(EventFields.EXCEPTION_STACKTRACE_FIELD, getStackTrace(t));
    sendMessage(jsonObject);
  }

  public void trace(String message) {
    JsonObject jsonObject = new JsonObject().putString(EventFields.TYPE_FIELD, EventFields.TRACE_EVENT)
      .putString(EventFields.TRACE_MESSAGE_FIELD, message);
    sendMessage(jsonObject);
  }

  public void register(final String testName, final Handler<Void> handler) {
    Handler<Message<JsonObject>> h = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> msg) {
        if (EventFields.START_TEST_EVENT.equals(msg.body.getString(EventFields.TYPE_FIELD)) &&
            testName.equals(msg.body.getString(EventFields.START_TEST_NAME_FIELD))) {
          handler.handle(null);
        }
      }
    };
    EventBus.instance.registerHandler(TestBase.EVENTS_ADDRESS, h);
    handlers.put(testName, h);
  }

  public void registerTests(final Object obj) {
    Method[] methods = obj.getClass().getMethods();
    for (final Method method: methods) {
      if (method.getName().startsWith("test")) {
        register(method.getName(), new SimpleHandler() {
          public void handle() {
            try {
              method.invoke(obj, (Object[])null);
            } catch (Exception e) {
              log.error("Failed to invoke test", e);
            }
          }
        });
      }
    }
  }

  public void unregisterAll() {
    for (Handler<Message<JsonObject>> handler: handlers.values()) {
      EventBus.instance.unregisterHandler(TestBase.EVENTS_ADDRESS, handler);
    }
    handlers.clear();
  }

  private void sendMessage(JsonObject msg) {
    try {
      EventBus.instance.send(TestBase.EVENTS_ADDRESS, msg);
    } catch (Exception e) {
      log.error("Failed to send message", e);
    }
  }

  public void sendEvent(String eventName) {
    JsonObject msg = new JsonObject().putString(EventFields.TYPE_FIELD, eventName);
    sendMessage(msg);
  }

  private String getStackTrace(Throwable t) {
    Writer result = new StringWriter();
    PrintWriter printWriter = new PrintWriter(result);
    t.printStackTrace(printWriter);
    return result.toString();
  }

  public static Buffer generateRandomBuffer(int length) {
    return generateRandomBuffer(length, false, (byte) 0);
  }

  public static byte[] generateRandomByteArray(int length) {
    return generateRandomByteArray(length, false, (byte) 0);
  }

  public static byte[] generateRandomByteArray(int length, boolean avoid, byte avoidByte) {
    byte[] line = new byte[length];
    for (int i = 0; i < length; i++) {
      //Choose a random byte - if we're generating delimited lines then make sure we don't
      //choose first byte of delim
      byte rand;
      do {
        rand = (byte) ((int) (Math.random() * 255) - 128);
      } while (avoid && rand == avoidByte);

      line[i] = rand;
    }
    return line;
  }

  public static Buffer generateRandomBuffer(int length, boolean avoid, byte avoidByte) {
    byte[] line = generateRandomByteArray(length, avoid, avoidByte);
    return Buffer.create(line);
  }

  public static String randomUnicodeString(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char c;
      do {
        c = (char) (0xFFFF * Math.random());
      } while ((c >= 0xFFFE && c <= 0xFFFF) || (c >= 0xD800 && c <= 0xDFFF)); //Illegal chars
      builder.append(c);
    }
    return builder.toString();
  }

  public static String randomAlphaString(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char c = (char) (65 + 25 * Math.random());
      builder.append(c);
    }
    return builder.toString();
  }

  public static boolean buffersEqual(Buffer b1, Buffer b2) {
    if (b1.length() != b2.length()) return false;
    for (int i = 0; i < b1.length(); i++) {
      if (b1.getByte(i) != b2.getByte(i)) return false;
    }
    return true;
  }

  public static boolean byteArraysEqual(byte[] b1, byte[] b2) {
    if (b1.length != b2.length) return false;
    for (int i = 0; i < b1.length; i++) {
      if (b1[i] != b2[i]) return false;
    }
    return true;
  }

  public void checkContext() {
    if (contextID == null) {
      throw new IllegalStateException("Don't call checkContext if utils were created with a null context");
    }
    azzert(th == Thread.currentThread(), "Expected:" + th + " Actual:" + Thread.currentThread());
    azzert(contextID.equals(Vertx.instance.getContextID()), "Expected:" + contextID + " Actual:" + Vertx.instance
        .getContextID());
  }

}
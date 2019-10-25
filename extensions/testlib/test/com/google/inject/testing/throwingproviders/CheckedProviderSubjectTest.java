package com.google.inject.testing.throwingproviders;

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.testing.throwingproviders.CheckedProviderSubject.assertThat;

import com.google.common.truth.ExpectFailure;
import com.google.common.truth.SimpleSubjectBuilder;
import com.google.inject.throwingproviders.CheckedProvider;
import com.google.inject.throwingproviders.CheckedProviders;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link CheckedProviderSubject}.
 *
 * @author eatnumber1@google.com (Russ Harmon)
 */
@RunWith(JUnit4.class)
public class CheckedProviderSubjectTest {
  public @Rule ExpectFailure expect = new ExpectFailure();

  @Test
  public void providedValue_gotExpected_expectSuccess() {
    String expected = "keep Summer safe";
    CheckedProvider<String> provider = CheckedProviders.of(StringCheckedProvider.class, expected);

    assertThat(provider).providedValue().isEqualTo(expected);
  }

@Test
  public void providedValue_gotUnexpected_expectFailure() {
    String expected = "keep Summer safe";
    String unexpected = "Summer is unsafe";
    CheckedProvider<String> provider = CheckedProviders.of(StringCheckedProvider.class, unexpected);
    String message =
        String.format(
            new StringBuilder().append("value of           : checkedProvider.get()\n").append("expected           : %s\n").append("but was            : %s\n").append("checkedProvider was: %s").toString(),
            expected, unexpected, getReturningProviderName(unexpected));

    expectWhenTesting().that(provider).providedValue().isEqualTo(expected);
    assertThat(expect.getFailure()).hasMessageThat().isEqualTo(message);
  }

@Test
  public void providedValue_throws_expectFailure() {
    CheckedProvider<String> provider =
        CheckedProviders.throwing(StringCheckedProvider.class, SummerException.class);
    String message =
        String.format(
            new StringBuilder().append("value of           : checkedProvider.get()\n").append("checked provider was not expected to throw an exception\n").append("checkedProvider was: %s").toString(),
            getThrowingProviderName(SummerException.class.getName()));

    expectWhenTesting().that(provider).providedValue();
    AssertionError expected = expect.getFailure();
    assertThat(expected).hasCauseThat().isInstanceOf(SummerException.class);
    assertThat(expected).hasMessageThat().isEqualTo(message);
  }

@Test
  public void thrownException_threwExpected_expectSuccess() {
    CheckedProvider<?> provider =
        CheckedProviders.throwing(StringCheckedProvider.class, SummerException.class);

    assertThat(provider).thrownException().isInstanceOf(SummerException.class);
  }

@Test
  public void thrownException_threwUnexpected_expectFailure() {
    Class<? extends Throwable> expected = SummerException.class;
    Class<? extends Throwable> unexpected = UnsupportedOperationException.class;
    CheckedProvider<String> provider =
        CheckedProviders.throwing(StringCheckedProvider.class, unexpected);
    String message =
        String.format(
            new StringBuilder().append("value of            : checkedProvider.get()'s exception\n").append("expected instance of: %s\n").append("but was instance of : %s\n").append("with value          : %s\n").append("checkedProvider was : %s").toString(),
            SummerException.class.getName(),
            UnsupportedOperationException.class.getName(),
            UnsupportedOperationException.class.getName(),
            getThrowingProviderName(UnsupportedOperationException.class.getName()));

    expectWhenTesting().that(provider).thrownException().isInstanceOf(expected);
    assertThat(expect.getFailure()).hasMessageThat().isEqualTo(message);
  }

@Test
  public void thrownException_gets_expectFailure() {
    String getValue = "keep WINTER IS COMING safe";
    CheckedProvider<String> provider = CheckedProviders.of(StringCheckedProvider.class, getValue);
    String message = String.format("expected to throw%nbut provided: %s", getValue);

    expectWhenTesting().that(provider).thrownException();
    assertThat(expect.getFailure()).hasMessageThat().isEqualTo(message);
  }

private SimpleSubjectBuilder<
          CheckedProviderSubject<String, CheckedProvider<String>>, CheckedProvider<String>>
      expectWhenTesting() {
    return expect
        .whenTesting()
        .about(CheckedProviderSubject.<String, CheckedProvider<String>>checkedProviders());
  }

private String getReturningProviderName(String providing) {
    return String.format("generated CheckedProvider returning <%s>", providing);
  }

private String getThrowingProviderName(String throwing) {
    return String.format("generated CheckedProvider throwing <%s>", throwing);
  }

private interface StringCheckedProvider extends CheckedProvider<String> {}

  private static final class SummerException extends RuntimeException {}
}

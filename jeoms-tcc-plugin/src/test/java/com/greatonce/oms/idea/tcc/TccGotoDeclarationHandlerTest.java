package com.greatonce.oms.idea.tcc;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.codeInsight.navigation.GotoImplementationHandler;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import java.util.ArrayList;
import java.util.List;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.LightProjectDescriptor;

/**
 * Verify navigation with real Java PSI
 * @author ajie
 */
public final class TccGotoDeclarationHandlerTest extends LightJavaCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor PROJECT = new LightProjectDescriptor() {
    /** Use the running JDK for method reference resolution */
    @Override
    public Sdk getSdk() {
      return JavaSdk.getInstance().createJdk("test", System.getProperty("java.home"), false);
    }
  };

  /** Use a real JDK rather than the source checkout mock JDK */
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PROJECT;
  }

  /** Verify plugin registration in the platform extension point */
  public void testExtensionRegistered() {
    assertTrue(GotoDeclarationHandler.EP_NAME.getExtensionList().stream()
        .anyMatch(handler -> handler instanceof TccGotoDeclarationHandler));
  }

  /** Verify native navigation exposes both chooser targets */
  public void testNativeNavigation() {
    myFixture.configureByText("Use.java",
        "class Use { void call(sample.Parent action) { action.<caret>prepare(null); } }");
    PsiElement[] result = GotoDeclarationAction.findAllTargetElements(
        getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertNotNull(result);
    assertEquals(2, result.length);
    assertEquals("doPrepare", ((PsiMethod) result[0]).getName());
    assertEquals("prepare", ((PsiMethod) result[1]).getName());
  }

  /** Verify the existing action id routes shortcuts and menu commands through the adapter */
  public void testImplementationActionRegistered() {
    var action = ActionManager.getInstance().getAction("GotoImplementation");
    assertInstanceOf(action, TccGotoImplementationAction.class);
    assertNotNull(action.getTemplatePresentation().getText());
    assertTrue(action.getShortcutSet().getShortcuts().length > 0);
    var menu = (ActionGroup) ActionManager.getInstance().getAction("GoToMenu");
    assertTrue(containsAction(menu, action, 5));
    var popup = (ActionGroup) ActionManager.getInstance().getAction("EditorPopupMenu");
    assertTrue(containsAction(popup, action, 5));
  }

  /** Inspect nested menu groups using the registered action instance */
  private boolean containsAction(ActionGroup group, AnAction action, int depth) {
    if (depth == 0) {
      return false;
    }
    for (AnAction child : group.getChildren(null)) {
      if (child == action || child instanceof ActionGroup nested && containsAction(nested, action, depth - 1)) {
        return true;
      }
    }
    return false;
  }

  /** Verify implementation navigation for every phase at the end of the reference name */
  public void testImplementationPhases() {
    for (String phase : new String[]{"prepare", "commit", "rollback"}) {
      myFixture.configureByText("Use.java", "class Use { void call(sample.Parent action) { action."
          + phase + "<caret>(null); } }");
      var data = implementationData();
      assertNotNull(data);
      assertEquals(1, data.targets.length);
      assertEquals("do" + Character.toUpperCase(phase.charAt(0)) + phase.substring(1),
          ((PsiMethod) data.targets[0]).getName());
      assertEquals("sample.Parent", ((PsiMethod) data.targets[0]).getContainingClass().getQualifiedName());
    }
  }

  /** Respect the closest override without returning unrelated implementations */
  public void testImplementationOverride() {
    myFixture.configureByText("Use.java", """
        class Child extends sample.Parent {
          protected String doPrepare(String input) { return input; }
          void call() { super.<caret>prepare(null); }
        }
        """);
    var data = implementationData();
    assertNotNull(data);
    assertEquals(1, data.targets.length);
    assertEquals("Child", ((PsiMethod) data.targets[0]).getContainingClass().getQualifiedName());
  }

  /** Ordinary final methods retain the native implementation target */
  public void testImplementationFallback() {
    myFixture.configureByText("Use.java", """
        class Use {
          final void prepare() {}
          void call() { <caret>prepare(); }
        }
        """);
    var data = implementationData();
    assertNotNull(data);
    assertEquals(1, data.targets.length);
    assertEquals("prepare", ((PsiMethod) data.targets[0]).getName());
    assertEquals("Use", ((PsiMethod) data.targets[0]).getContainingClass().getQualifiedName());
  }

  /** Invoke the handler supplied by the registered implementation action */
  private com.intellij.codeInsight.navigation.GotoTargetHandler.GotoData implementationData() {
    var action = (TccGotoImplementationAction) ActionManager.getInstance().getAction("GotoImplementation");
    return ((GotoImplementationHandler) action.getHandler())
        .getSourceAndTargetElements(myFixture.getEditor(), myFixture.getFile());
  }

  /** Add the generic template and an intermediate implementation */
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("""
        package com.greatonce.oms.db.tcc;
        public abstract class AbstractOmsTccAction<T, R> {
          public final R prepare(T input) { return doPrepare(input); }
          public final boolean commit(Object context) { return doCommit(null, context); }
          public final boolean rollback(Object context) { return doRollback(null, context); }
          protected abstract R doPrepare(T input);
          protected abstract boolean doCommit(T input, Object context);
          protected abstract boolean doRollback(T input, Object context);
        }
        """);
    myFixture.addClass("""
        package sample;
        public class Parent extends com.greatonce.oms.db.tcc.AbstractOmsTccAction<String, String> {
          protected String doPrepare(String input) { return input; }
          protected boolean doCommit(String input, Object context) { return true; }
          protected boolean doRollback(String input, Object context) { return true; }
        }
        """);
  }

  /** Verify all phases and generic inherited implementations */
  public void testPhases() {
    for (String phase : new String[]{"prepare", "commit", "rollback"}) {
      assertHook("class Child extends sample.Parent { void call() { this.<caret>" + phase
          + "(null); } }", "do" + Character.toUpperCase(phase.charAt(0)) + phase.substring(1), "sample.Parent");
    }
  }

  /** Verify the closest override wins over its parent */
  public void testOverrideAndOverload() {
    assertHook("""
        class Child extends sample.Parent {
          protected String doPrepare(String input) { return input; }
          protected String doPrepare(Integer unrelated) { return ""; }
          void call() { <caret>prepare(null); }
        }
        """, "doPrepare", "Child");
  }

  /** Super template calls still dispatch hooks on the current object */
  public void testSuperCall() {
    assertHook("""
        class Child extends sample.Parent {
          protected String doPrepare(String input) { return input; }
          void call() { super.<caret>prepare(null); }
        }
        """, "doPrepare", "Child");
  }

  /** Verify a qualified call from another class */
  public void testQualifiedCall() {
    assertHook("class Use { void call(sample.Parent action) { action.<caret>commit(null); } }",
        "doCommit", "sample.Parent");
  }

  /** Verify bound method references */
  public void testMethodReference() {
    assertHook("""
        interface Operation { String run(String input); }
        class Use { Operation call(sample.Parent action) { return action::<caret>prepare; } }
        """, "doPrepare", "sample.Parent");
  }

  /** Verify unbound method references */
  public void testUnboundMethodReference() {
    assertHook("""
        interface Operation { String run(sample.Parent action, String input); }
        class Use { Operation call() { return sample.Parent::<caret>prepare; } }
        """, "doPrepare", "sample.Parent");
  }

  /** Do not navigate to a parent implementation hidden by an abstract override */
  public void testAbstractOverride() {
    assertNull(targets("""
        abstract class Child extends sample.Parent {
          protected abstract String doPrepare(String input);
          void call() { <caret>prepare(null); }
        }
        """));
  }

  /** Leave ordinary methods and unrelated overloads untouched */
  public void testFallback() {
    assertNull(targets("class Use { void prepare() {} void call() { <caret>prepare(); } }"));
    assertNull(targets("""
        class Child extends sample.Parent {
          void prepare(int input) {}
          void call() { <caret>prepare(1); }
        }
        """));
    assertNull(targets("""
        class Use {
          void call(com.greatonce.oms.db.tcc.AbstractOmsTccAction<String, String> action) {
            action.<caret>prepare(null);
          }
        }
        """));
  }

  /** Find template callers of all three hooks through the registered native handler */
  public void testReversePhases() {
    for (String phase : new String[]{"prepare", "commit", "rollback"}) {
      String hookName = "do" + Character.toUpperCase(phase.charAt(0)) + phase.substring(1);
      myFixture.configureByText("Use.java", """
          class Inherited extends sample.Parent {}
          class Use {
            void call(sample.Parent action, Inherited inherited) {
              action.%s(null);
              inherited.%s(null);
            }
          }
          """.formatted(phase, phase));
      assertEquals(List.of("action." + phase, "inherited." + phase),
          reverseUsages(hookName, GlobalSearchScope.projectScope(getProject())));
    }
  }

  /** Exclude overridden and unknown receivers while keeping direct calls and method references */
  public void testReverseDispatchAndScope() {
    myFixture.configureByText("Use.java", """
        interface Operation { String run(String value); }
        class Other extends sample.Parent {
          protected String doPrepare(String input) { return input; }
        }
        class Use extends sample.Parent {
          void call(sample.Parent action, Other other,
              com.greatonce.oms.db.tcc.AbstractOmsTccAction<String, String> unknown) {
            action.prepare(null);
            other.prepare(null);
            unknown.prepare(null);
            doPrepare(null);
            Operation reference = action::prepare;
          }
        }
        """);
    assertEquals(List.of("action.prepare", "action::prepare", "doPrepare"),
        reverseUsages("doPrepare", new LocalSearchScope(myFixture.getFile())));
    var otherFile = myFixture.addClass("class Unrelated { void call(sample.Parent action) { action.prepare(null); } }")
        .getContainingFile();
    assertEquals(List.of("action.prepare"), reverseUsages("doPrepare", new LocalSearchScope(otherFile)));
    assertEquals(1, ReferencesSearch.search(parentHook("doPrepare"),
        new LocalSearchScope(myFixture.getFile())).findAll().size());
  }

  /** Respect search cancellation and ignore methods that do not override a hook */
  public void testReverseCancellationAndFallback() {
    var unrelated = myFixture.addClass("class Unrelated { void doPrepare(String input) {} }");
    assertFalse(new TccFindUsagesHandlerFactory().canFindUsages(unrelated.getMethods()[0]));
    myFixture.configureByText("Use.java",
        "class Use { void call(sample.Parent action) { action.prepare(null); action.prepare(null); } }");
    PsiMethod hook = parentHook("doPrepare");
    var handler = new FindUsagesManager(getProject()).getFindUsagesHandler(hook, false);
    assertNotNull(handler);
    var options = new FindUsagesOptions(new LocalSearchScope(myFixture.getFile()));
    options.isUsages = true;
    options.isSearchForTextOccurrences = false;
    int[] count = {0};
    assertFalse(handler.processElementUsages(hook, usage -> { count[0]++; return false; }, options));
    assertEquals(1, count[0]);
  }

  /** Collect native usage results within the requested scope */
  private List<String> reverseUsages(String hookName, SearchScope scope) {
    PsiMethod hook = parentHook(hookName);
    var handler = new FindUsagesManager(getProject()).getFindUsagesHandler(hook, false);
    assertNotNull(handler);
    assertTrue(handler.getClass().getName().startsWith(TccFindUsagesHandlerFactory.class.getName()));
    var options = new FindUsagesOptions(scope);
    options.isUsages = true;
    options.isSearchForTextOccurrences = false;
    List<String> result = new ArrayList<>();
    assertTrue(handler.processElementUsages(hook, usage -> {
      result.add(usage.getElement().getText());
      return true;
    }, options));
    return result.stream().sorted().toList();
  }

  /** Locate the fixture implementation */
  private PsiMethod parentHook(String name) {
    var parent = JavaPsiFacade.getInstance(getProject())
        .findClass("sample.Parent", GlobalSearchScope.projectScope(getProject()));
    assertNotNull(parent);
    return parent.findMethodsByName(name, false)[0];
  }

  /** Assert the native chooser contains the hook followed by the template */
  private void assertHook(String source, String name, String owner) {
    PsiElement[] result = targets(source);
    assertNotNull(result);
    assertEquals(2, result.length);
    PsiMethod hook = (PsiMethod) result[0];
    assertEquals(name, hook.getName());
    assertEquals(owner, hook.getContainingClass().getQualifiedName());
    assertEquals("com.greatonce.oms.db.tcc.AbstractOmsTccAction",
        ((PsiMethod) result[1]).getContainingClass().getQualifiedName());
  }

  /** Run the handler at the marked reference */
  private PsiElement[] targets(String source) {
    myFixture.configureByText("Use.java", source);
    int offset = myFixture.getCaretOffset();
    return new TccGotoDeclarationHandler().getGotoDeclarationTargets(
        myFixture.getFile().findElementAt(offset), offset, myFixture.getEditor());
  }
}

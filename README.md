# Stateful Generative testing using Spec Models

Illustrates how to use Clojure Spec to test a webapp that stores data.

This was used as a talk at Clojure Sydney Meetup on Sep 26th, 2017.

## TL;DR

Clone and run tests using `lein test`

Look at [web-crud.clj](https://github.com/stevebuik/stateful-generative-tests/blob/master/src/stateful_testing/web_crud.clj) and use the comments at the bottom to start the ring server to see the UI

Look at [web-crud-tests.clj](https://github.com/stevebuik/stateful-generative-tests/blob/master/test/stateful_testing/web_crud_tests.clj) to see how to use generated commands to test Add/Delete in the webapp.

This test combines Kerodon, Clojure Spec and custom generators to generate valid sequences of *commands* for a web-app.

## The Long Version.....

Generative testing reduces the need for example-based unit tests.
Clojure Spec takes this further by automatically providing generators to test clojure functions.

**Question**: the generated data is stateless. Webapps are stateful. How can these two ideas be combined?

**Answer**: search the interwebs....and find...

**Stateful Generator Libraries**

https://github.com/jstepien/states

https://github.com/czan/stateful-check

**Blog Posts**

[Verifying FSMs using test.check by Guillermo Winkler](http://blog.guillermowinkler.com/blog/2015/04/12/verifying-state-machine-behavior-using-test-dot-check/)

**Videos**

[Customising Generators by Stu Halloway](https://www.youtube.com/watch?v=WoFkhE92fqc)

[Teleport Testing by Antonio Montiero & Mike Kaplinskiy](https://www.youtube.com/watch?v=qijWBPYkRAQ)

Thanks to all these people for sharing such valuable work. It inspired this presentation.

The blog post provides a great explanation and sample code for stateful testing.
It could even be written as portable (cljc) Clojure - sweet! It would be great to see tests running in this readme.

In the blog post, the section on shrinking and Rose Trees is really interesting.

Stu's video demonstrates the idea of generator models to make Spec generators smarter.
Maybe using the code from the blog as a spec model could work? Let's try.

### To the REPL....

(follow the links and/or run the tests in your IDE)

[Experiment #1](https://github.com/stevebuik/stateful-generative-tests/blob/master/test/stateful_testing/states_lib_tests.clj)
: run the sample code for the *states* library.

Works well but is not portable Clojure. Leaving this path alone for now.

[Experiment #2](https://github.com/stevebuik/stateful-generative-tests/blob/master/test/stateful_testing/fsm_tests.clj)
: run the FSM sample code from the blog post

* observe see the two phases:
    * cmd-seq is the generation phase
    * prop/for-all is the application phase
* exec fn is used in the generation phase to maintain the state.
this means that the generation state system is different from the state of the system under test. Could having two state implementations be a source of bugs as complexity grows?
* the test invariant in this example is not a good example
* Clojure Spec is not used anywhere (because the blog was written before Spec)

[Experiment #3](https://github.com/stevebuik/stateful-generative-tests/blob/master/test/stateful_testing/fsm_tests2.clj)
: changed the FSM sample to test a set (like the *states* test) instead of vector

* added a :clear-cmd for emptying the set
* still have different code for gen vs application phase
* test.check invariant more like a real world example
* deftest ensures that *true* is the result since test.check puts exceptions in the :result

[Experiment #4](https://github.com/stevebuik/stateful-generative-tests/blob/master/test/stateful_testing/fsm_tests3.clj)
: changed the FSM sample to use same state mgmt fn for gen and application phase

* easier to read, DRY code
* still not using Spec

[Experiment #5](https://github.com/stevebuik/stateful-generative-tests/blob/master/test/stateful_testing/fsm_tests4.clj)
: changed the FSM sample to use a Spec for the commands

* play with the spec by running the code in the comments. compare the stateless vs the stateful generated commands
* using a spec for the *apply-commands* fn which means that prop/for-all invariants are no longer required.
this is the driver fn for the generative tests.
* uncomment the two *pprint* lines to see what was tested

### Testing a Web-app instead of a Set

Load the [web-crud.clj](https://github.com/stevebuik/stateful-generative-tests/blob/master/src/stateful_testing/web_crud.clj)
file and run the two expressions in the comment at the bottom, then browse `http://localhost:8080/list`
and play with the app to understand it

Load the [web-crud-tests.clj](https://github.com/stevebuik/stateful-generative-tests/blob/master/test/stateful_testing/web_crud_tests.clj)
file and run:

1. the expressions in comments
2. the example-based unit test
3. the generative test
4. try breaking it by changing the default id in the add/exec fn

and observe....

* Kerodon is awesome. Like a fast Selenium
* Add commands don't include an :id since the webapp generates the id
* Use a multi-spec since now commands have different keys
* Using a spec'd driver fn, like in Experiment #5
* It's fast! Even running 50 generated command sequences is sub-second.

Originally I used the web-app for the generation and the application phase.
This did not work because each generated command sequence retained state from previous sequences.
The solution was to go back to two systems for state, one for each phase.

When this test is run, the number of assertions is high. This is because every CRUD operation asserts that status = 200 etc when the command is applied.
This is the power of generative tests, many combinations generated, applied and asserted.

### Conclusions

Although there are two good libraries for stateful testing, I prefer the blog posts solution because the generated commands are pure data (no fns as values).
This makes them easier to read, easier to send over a wire for remote invocation and the code could easily be portable (cljc).

The combination of Kerodon and generated commands is a Selenium killer. Happy days!
That said, there is no browser so Selenium is better if you are seeking cross-browser testing.

This testing technique replaces test.check with Specs and test.check underneath.
The amount of code is approx the same but, with Specs, you also have command DSL that can be used for other purposes
e.g.
* runtime request validation
* remote command(s) execution

These are powerful benefits so testing this way is a valuable investment.

### Future

* A server endpoint could accept an EDN sequence of commands and run them as a live test i.e. generative Selenium
* Single Page Apps are stateful and can be tested the same way (see the Teleport video for more)
* Convert Set tests to portable Clojure and run using Klipse



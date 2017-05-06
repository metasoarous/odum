
# odum

This is a tool for designing and analyzing Odum Diagrams (or graphs/networks).
See <https://en.wikipedia.org/wiki/Howard_T._Odum>.

## Usage

Execute `lein repl` and when that finishes booting execute `(run)` from the REPL.
You should get some data loading on the client when you hit <localhost:9030>.

You should see some draggable nodes on an svg canvas.
When you click on the text, it will bring down a little form for editing the information for a node.

From there:
* check out `src/cljc/odum/db.cljc`, which has the db schema and default database, as well as some energy flow
  simultation.
* then look at `src/cljs/odum/app.cljs`, which has the view code.
* have fun!

## Notes

This app was built using various parts of [Datsys](https://github.com/metasoarous/datsys).


function (doc, oldDoc, meta) {
  channel(doc._id);
  access('admin', doc._id);
}
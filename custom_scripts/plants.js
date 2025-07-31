function (doc, oldDoc, meta) {
  channel(doc.plant_id);
  access('admin', doc.plant_id);
}
DogFood Embeddable Gallery
==========================

Direct iframe:

<iframe
  src="https://YOUR-DOGFOOD-HOST/embed/gallery.html"
  title="DogFood project gallery"
  style="width:100%;min-height:520px;border:0;"
></iframe>

Script embed:

<script
  src="https://YOUR-DOGFOOD-HOST/embed/gallery.js"
  data-search=""
  data-track=""
></script>

Optional query parameters:
- search
- track
- api

The gallery reads from:
  /api/v1/projects

Only submitted projects returned by the public API are displayed.

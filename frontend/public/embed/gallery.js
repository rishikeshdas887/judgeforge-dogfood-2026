(function () {
  "use strict";

  const currentScript =
    document.currentScript;

  if (!currentScript) {
    return;
  }

  const iframe =
    document.createElement("iframe");

  const src =
    new URL(
      "gallery.html",
      currentScript.src
    );

  for (const name of [
    "search",
    "track",
    "api"
  ]) {
    const value =
      currentScript.dataset[name];

    if (value) {
      src.searchParams.set(
        name,
        value
      );
    }
  }

  iframe.src = src.toString();
  iframe.title =
    "DogFood project gallery";
  iframe.loading = "lazy";
  iframe.style.width = "100%";
  iframe.style.minHeight = "520px";
  iframe.style.border = "0";
  iframe.style.display = "block";
  iframe.style.borderRadius = "16px";

  currentScript.parentNode.insertBefore(
    iframe,
    currentScript
  );
})();

import { createCn } from "cn/config";

/** Class merger that knows the Zuno type ramp defined in globals.css (`text-hero`, `text-label`, …). */
export const cn = createCn({
  extend: {
    classGroups: {
      "font-size": [
        {
          text: [
            "hero",
            "heading",
            "quote",
            "title",
            "lead",
            "body-lg",
            "body",
            "label",
            "footnote",
            "eyebrow",
          ],
        },
      ],
      "text-color": [{ text: ["faint"] }],
      "border-color": [{ border: ["border-strong"] }],
      "bg-color": [{ bg: ["elevated"] }],
    },
  },
});

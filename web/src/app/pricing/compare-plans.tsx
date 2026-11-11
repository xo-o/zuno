import { Check } from "lucide-react";
import { Fragment } from "react";
import { Container } from "@/components/site/site-page";
import { cn } from "@/lib/utils";

type Value = string | boolean;

const GROUPS: { title: string; rows: [string, Value, Value][] }[] = [
  {
    title: "Practice",
    rows: [
      ["Voice conversations", "10 min / day", "Unlimited"],
      ["Typed Free Talk", "Unlimited", "Unlimited"],
      ["Topics & Roleplays", "3 each", "Unlimited"],
      ["Live corrections & translations", true, true],
    ],
  },
  {
    title: "Learning tools",
    rows: [
      ["Photos, PDFs & links in chats", false, true],
      ["Level checks & detailed feedback", false, true],
      ["Scheduled practice & reminders", true, true],
      ["Languages & accents", "All 30+", "All 30+"],
    ],
  },
  {
    title: "Support",
    rows: [["Email support", "Standard", "Priority"]],
  },
];

function Cell({ value }: { value: Value }) {
  if (value === true) {
    return (
      <>
        <Check
          aria-hidden="true"
          className="mx-auto block size-[18px] text-foreground"
        />
        <span className="sr-only">Included</span>
      </>
    );
  }
  if (value === false) {
    return (
      <>
        <span
          aria-hidden="true"
          className="block text-[13px] leading-[18px] text-faint md:text-sm md:leading-5"
        >
          —
        </span>
        <span className="sr-only">Not included</span>
      </>
    );
  }
  return (
    <span className="block text-[13px] leading-[18px] text-muted-foreground md:text-sm md:leading-5">
      {value}
    </span>
  );
}

const featureCell = "pl-4 md:pl-6";
const valueCell = "w-[84px] pl-2 text-center md:w-[200px] md:pl-0";
const lastCell = "w-[100px] pr-4 md:w-[224px] md:pr-6";

export function ComparePlans() {
  return (
    <Container className="pb-14 md:pb-24">
      <section
        aria-labelledby="compare-plans"
        className="mx-auto flex w-full max-w-[904px] flex-col gap-8"
      >
        <h2
          id="compare-plans"
          className="text-2xl leading-[30px] font-semibold tracking-[-0.8px] text-foreground"
        >
          Compare plans
        </h2>
        <div className="overflow-hidden rounded-3xl border border-input">
          <table
            aria-labelledby="compare-plans"
            className="w-full table-fixed border-separate border-spacing-0 text-left"
          >
            <thead className="bg-muted">
              <tr className="text-sm leading-[18px] font-medium text-foreground">
                <th scope="col" className={cn(featureCell, "py-4 md:h-14")}>
                  <span className="sr-only">Feature</span>
                </th>
                <th scope="col" className={cn(valueCell, "py-4")}>
                  Free
                </th>
                <th scope="col" className={cn(valueCell, lastCell, "py-4")}>
                  Pro
                </th>
              </tr>
            </thead>
            <tbody>
              {GROUPS.map((group) => (
                <Fragment key={group.title}>
                  <tr className="bg-card *:border-t *:border-border">
                    <th
                      scope="colgroup"
                      colSpan={3}
                      className="h-[49px] px-4 text-eyebrow font-medium text-faint uppercase md:px-6"
                    >
                      {group.title}
                    </th>
                  </tr>
                  {group.rows.map(([feature, free, pro]) => (
                    <tr key={feature} className="*:border-t *:border-border">
                      <th
                        scope="row"
                        className={cn(
                          featureCell,
                          "py-4 text-body font-normal text-foreground md:h-[57px]",
                        )}
                      >
                        {feature}
                      </th>
                      <td className={cn(valueCell, "py-4")}>
                        <Cell value={free} />
                      </td>
                      <td className={cn(valueCell, lastCell, "py-4")}>
                        <Cell value={pro} />
                      </td>
                    </tr>
                  ))}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </Container>
  );
}

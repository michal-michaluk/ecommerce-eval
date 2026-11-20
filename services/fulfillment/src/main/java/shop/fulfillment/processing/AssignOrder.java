package shop.fulfillment.processing;

/**
 * The claim an employee makes on one order — {@code POST /processing/{processingId}/assignment}.
 *
 * <p>It carries no assignee: the claimer is the assignee (element 07 RULE-5, "some employee on the warehouse can assign
 * himself or herself"), so the caller is read from the token and never from the body. The record exists so the route states
 * its command, and the body stays empty.
 */
record AssignOrder() {
}

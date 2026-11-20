package shop.fulfillment.parcel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import shop.fulfillment.kernel.CarrierFixture;
import shop.fulfillment.kernel.CarrierIdentity;
import shop.fulfillment.kernel.CarrierPort;
import shop.fulfillment.kernel.CollectionHandle;
import shop.fulfillment.kernel.CollectionRequest;
import shop.fulfillment.kernel.LabelDocument;
import shop.fulfillment.kernel.LabelRequest;
import shop.fulfillment.kernel.LegObservation;
import shop.fulfillment.kernel.ParcelSequence;
import shop.fulfillment.kernel.Registration;
import shop.fulfillment.kernel.RegistrationRequest;

/**
 * The carrier port as a test double: every registration, label, collection and status call recorded, one waybill per
 * registration, and a status list the test decides.
 *
 * <p>It is public because the e2e harness wires it as the application's carrier bean (spec D14: the port plus an in-test fake,
 * no HTTP). A registration answers a waybill per reference, so two orders of one suite get two identities, the way a carrier
 * would assign them.
 */
public class FakeCarrierPort implements CarrierPort {

    static final String WAYBILL = "0234567890123";
    static final byte[] DOCUMENT = {'%', 'P', 'D', 'F', '-', '1'};
    static final String COLLECTION = "COL-0001";

    final List<String> calls = new ArrayList<>();
    final List<RegistrationRequest> registered = new ArrayList<>();
    final List<CollectionRequest> collected = new ArrayList<>();
    private final Map<String, CarrierIdentity> identities = new LinkedHashMap<>();
    private List<LegObservation> statuses = List.of();
    private RuntimeException failure;
    private int failingCall = Integer.MAX_VALUE;

    FakeCarrierPort withStatuses(List<LegObservation> observations) {
        this.statuses = List.copyOf(observations);
        return this;
    }

    FakeCarrierPort failing(RuntimeException cause) {
        this.failure = cause;
        this.failingCall = calls.size() + 1;
        return this;
    }

    /** One distinct waybill per reference, the number a carrier would consume per registration. */
    CarrierIdentity identityOf(String reference) {
        return identities.computeIfAbsent(reference,
                registered -> new CarrierIdentity(WAYBILL + "-" + identities.size()));
    }

    @Override
    public Registration register(RegistrationRequest request) {
        record("register " + request.reference().value());
        registered.add(request);
        return CarrierFixture.aRegistrationOf(request.reference(), identityOf(request.reference().value()),
                new ParcelSequence(1, request.ofTotal()));
    }

    @Override
    public LabelDocument label(LabelRequest request) {
        record("label " + request.carrierIdentity().value());
        return new LabelDocument(DOCUMENT, request.format());
    }

    @Override
    public CollectionHandle requestCollection(CollectionRequest request) {
        record("requestCollection " + request.carrierIdentity().value());
        collected.add(request);
        return new CollectionHandle(COLLECTION);
    }

    @Override
    public List<LegObservation> status(CarrierIdentity identity) {
        record("status " + identity.value());
        return statuses;
    }

    private void record(String call) {
        calls.add(call);
        if (calls.size() >= failingCall) {
            throw failure;
        }
    }

    public List<String> callsStartingWith(String prefix) {
        return calls.stream().filter(call -> call.startsWith(prefix)).toList();
    }
}

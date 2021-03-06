package org.treblereel.client.events;

import java.util.Random;

import javax.annotation.PostConstruct;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;

import elemental2.dom.HTMLButtonElement;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLInputElement;
import elemental2.dom.MouseEvent;
import org.jboss.elemento.IsElement;
import org.treblereel.gwt.crysknife.annotation.DataField;
import org.treblereel.gwt.crysknife.annotation.EventHandler;
import org.treblereel.gwt.crysknife.annotation.ForEvent;
import org.treblereel.gwt.crysknife.annotation.Templated;

/**
 * @author Dmitrii Tikhomirov
 * Created by treblereel 3/31/19
 */
@Singleton
@Templated("beanwithcdievents.html")
public class BeanWithCDIEvents implements IsElement<HTMLDivElement> {

    @Inject
    @DataField
    protected HTMLDivElement form;

    @Inject
    @DataField
    protected HTMLInputElement textBox;

    @Inject
    protected Event<User> eventUser;

    @Inject
    protected Event<Address> eventAddress;

    @Inject
    @DataField
    protected HTMLButtonElement sendUserEvent, sendAddressEvent;

    @PostConstruct
    public void init() {
        initBtn();
    }

    private void initBtn() {
        sendUserEvent.addEventListener("click", evt -> {
            User user = new User();
            user.setId(new Random().nextInt());
            user.setName("IAMUSER");
            eventUser.fire(user);
        });

        sendAddressEvent.addEventListener("click", evt -> {
            Address address = new Address();
            address.setId(new Random().nextInt());
            address.setName("Redhat");
            eventAddress.fire(address);
        });
    }

    public void onUserEvent(@Observes User user) {
        setText(user.toString());
    }

    public void onAddressEvent(@Observes Address address) {
        setText(address.toString());
    }

    private void setText(String text) {
        textBox.value = text;
    }

    @Override
    public HTMLDivElement element() {
        return form;
    }

    @EventHandler("sendAddressEvent")
    protected void sendAddressEvent(@ForEvent("click") final MouseEvent event) {
        Address address = new Address();
        address.setId(new Random().nextInt());
        address.setName("Redhat");
        eventAddress.fire(address);
    }

    @EventHandler("sendUserEvent")
    protected void sendUserEvent(@ForEvent("click") final MouseEvent event) {
        User user = new User();
        user.setId(new Random().nextInt());
        user.setName("IAMUSER");
        eventUser.fire(user);
    }
}

package model.telegram.commands;

import model.User;
import utils.UOpts;

import static utils.TextUtils.notNull;

/**
 * @author Denis Danilin | denis@danilin.name
 * 16.05.2020
 * tfs ☭ sweat and blood
 */
public final class CreateLabel extends ACommand implements TgCommand, GotUserInput {
    public final String name;

    public CreateLabel(final String name, final long msgId, final long callbackId, final User user) {
        super(msgId, user, callbackId);
        this.name = name;
        UOpts.WaitLabelText.clear(this);
    }

    @Override
    public String getInput() {
        return name;
    }
}
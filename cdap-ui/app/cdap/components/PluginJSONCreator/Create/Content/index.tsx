/*
 * Copyright © 2020 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import withStyles, { StyleRules, WithStyles } from '@material-ui/core/styles/withStyles';
import { STEPS } from 'components/PluginJSONCreator/Create/steps';
import {
  CreateContext,
  createContextConnect,
} from 'components/PluginJSONCreator/CreateContextConnect';
import * as React from 'react';

const styles = (theme): StyleRules => {
  return {
    comp: {
      borderRight: `1px solid ${theme.palette.grey[400]}`,
      width: '60%',
      color: `${theme.palette.grey[400]}`,
    },
  };
};

interface IContentProps {
  activeStep: number;
}

const ContentView: React.FC<IContentProps & WithStyles<typeof styles>> = ({
  classes,
  activeStep,
}) => {
  if (!STEPS[activeStep] || !STEPS[activeStep].component) {
    return null;
  }

  const Comp = STEPS[activeStep].component;
  return (
    <div>
      <Comp className={classes.comp} />
    </div>
  );
};

const StyledContentView = withStyles(styles)(ContentView);
const Content = createContextConnect(CreateContext, StyledContentView);
export default Content;
